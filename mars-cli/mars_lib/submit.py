import io
import os
from datetime import datetime
from io import TextIOWrapper
import time
import requests
import json
from typing import Any
from mars_lib.authentication import (
    get_metabolights_auth_token,
    get_webin_auth_token,
    load_credentials,
    AuthProvider,
)
from mars_lib.biosamples_external_references import (
    get_header,
    biosamples_endpoints,
    BiosamplesRecord,
    validate_json_against_schema,
    input_json_schema_filepath,
)
from mars_lib.credential import CredentialManager
from mars_lib.isa_json import (
    load_isa_json,
    reduce_isa_json_for_target_repo,
    update_isa_json,
    map_data_files_to_repositories,
)
from mars_lib.models.isa_json import Comment, IsaJson
from mars_lib.models.repository_response import RepositoryResponse
from mars_lib.target_repo import TargetRepository
from mars_lib.logging import print_and_log
from pydantic import ValidationError

from mars_lib.ftp_upload import FTPUploader
from pathlib import Path
from typing import List


def save_step_to_file(time_stamp: float, filename: str, isa_json: IsaJson):
    dir_path = f"tmp/{datetime.now().strftime('%Y-%m-%dT%H:%M:%S')}"
    os.makedirs(dir_path, exist_ok=True)

    with open(f"{dir_path}/{filename}.json", "w") as f:
        f.write(isa_json.model_dump_json(by_alias=True, exclude_none=True))


DEBUG = os.getenv("MARS_DEBUG") in ["1", 1]


def submission(
    webin_username: str,
    metabolights_username: str,
    metabolights_ftp_username: str,
    credentials_file: TextIOWrapper,
    isa_json_file: str,
    target_repositories: list[str],
    investigation_is_root: bool,
    urls: dict[str, Any],
    file_transfer: str,
    output: str,
    data_file_paths: List[TextIOWrapper] = [],
) -> None:
    # If credential manager info found:
    # Get password from the credential manager
    # Else:
    # read credentials from file
    if all([webin_username, metabolights_username, metabolights_ftp_username]):
        user_credentials = {
            cred_pair[0]: {
                "username": cred_pair[1],
                "password": CredentialManager(cred_pair[0]).get_password_keyring(
                    cred_pair[1]
                ),
            }
            for cred_pair in zip(
                AuthProvider.available_providers(),
                [webin_username, metabolights_username, metabolights_ftp_username],
            )
        }
    else:
        if credentials_file == "":
            raise ValueError("No credentials found")

        user_credentials = load_credentials(credentials_file)

    isa_json = load_isa_json(isa_json_file, investigation_is_root)

    # Guard clause to keep MyPy happy
    if isinstance(isa_json, ValidationError):
        raise ValidationError(f"ISA JSON is invalid: {isa_json}")

    print_and_log(
        f"ISA JSON with investigation '{isa_json.investigation.title}' is valid."
    )

    # create data file map
    data_file_map = map_data_files_to_repositories(
        files=[str(dfp) for dfp in data_file_paths], isa_json=isa_json
    )

    time_stamp = datetime.timestamp(datetime.now())

    if DEBUG:
        save_step_to_file(time_stamp, "0_Initial_ISA_JSON_in_model", isa_json)

    if all(
        repo not in TargetRepository.available_repositories()
        for repo in target_repositories
    ):
        raise ValueError("No target repository selected.")

    if TargetRepository.BIOSAMPLES.value in target_repositories:
        # Submit to Biosamples
        biosamples_result = submit_to_biosamples(
            isa_json=isa_json,
            biosamples_credentials=user_credentials[AuthProvider.WEBIN.value],
            biosamples_url=urls["BIOSAMPLES"]["SUBMISSION"],
            webin_token_url=urls["WEBIN"]["TOKEN"],
        )
        print_and_log(
            f"Submission to {TargetRepository.BIOSAMPLES.value} was successful. Result:\n{biosamples_result.json()}",
            level="info",
        )
        # Update `isa_json`, based on the receipt returned
        bs_mars_receipt = RepositoryResponse.model_validate(
            json.loads(biosamples_result.content)
        )
        isa_json = update_isa_json(isa_json, bs_mars_receipt)
        if DEBUG:
            save_step_to_file(time_stamp, "1_after_biosamples", isa_json)

    if TargetRepository.ENA.value in target_repositories:
        # Step 1 : upload data if file paths are provided
        if data_file_paths and file_transfer:
            upload_to_ena(
                file_paths=[
                    Path(df) for df in data_file_map[TargetRepository.ENA.value]
                ],
                user_credentials=user_credentials[AuthProvider.WEBIN.value],
                submission_url=urls["ENA"]["DATA-SUBMISSION"],
                file_transfer=file_transfer,
            )
        print_and_log(
            f"Start submitting to {TargetRepository.ENA.value}.", level="debug"
        )

        # Step 2 : submit isa-json to ena
        ena_result = submit_to_ena(
            isa_json=isa_json,
            user_credentials=user_credentials[AuthProvider.WEBIN.value],
            submission_url=urls["ENA"]["SUBMISSION"],
        )
        print_and_log(
            f"Submission to {TargetRepository.ENA.value} was successful. Result:\n{ena_result.json()}"
        )

        print_and_log(
            f"Update ISA-JSON based on receipt from {TargetRepository.ENA.value}.",
            level="debug",
        )
        ena_mars_receipt = RepositoryResponse.model_validate(
            json.loads(ena_result.content)
        )
        isa_json = update_isa_json(isa_json, ena_mars_receipt)
        if DEBUG:
            save_step_to_file(time_stamp, "2_after_ena", isa_json)

    if TargetRepository.METABOLIGHTS.value in target_repositories:
        # Submit to MetaboLights
        metabolights_result = upload_to_metabolights(
            file_paths=data_file_map[TargetRepository.METABOLIGHTS.value],
            file_transfer=file_transfer,
            isa_json=isa_json,
            metabolights_credentials=user_credentials[
                AuthProvider.METABOLIGHTS_METADATA.value
            ],
            metabolights_url=urls["METABOLIGHTS"]["SUBMISSION"],
            metabolights_token_url=urls["METABOLIGHTS"]["TOKEN"],
        )
        metabolights_receipt_obj = metabolights_result.json()
        print_and_log(
            f"Submission to {TargetRepository.METABOLIGHTS.value} was successful. Result:\n{metabolights_receipt_obj}",
            level="info",
        )
        metabolights_receipt = RepositoryResponse.model_validate(
            metabolights_receipt_obj
        )
        # TODO: MetaboLights creates accession number with errors. Errors are not handled.
        isa_json.investigation.studies[0].comments.append(
            Comment(
                name="metabolights_accession",
                value=metabolights_receipt.accessions[0].value,
            )
        )
        if DEBUG:
            save_step_to_file(time_stamp, "3_after_metabolights", isa_json)

    if TargetRepository.EVA.value in target_repositories:
        # Submit to EVA
        # TODO: Filter out other assays
        print_and_log(
            f"Submission to {TargetRepository.EVA.value} was successful.", level="info"
        )
        # TODO: Update `isa_json`, based on the receipt returned

    # Return the updated ISA JSON
    with open(f"{output}.json", "w") as f:
        f.write(isa_json.model_dump_json(by_alias=True, exclude_none=True))


def submit_to_biosamples(
    isa_json: IsaJson,
    biosamples_credentials: dict[str, str],
    webin_token_url: str,
    biosamples_url: str,
) -> requests.Response:
    params = {
        "webinjwt": get_webin_auth_token(
            biosamples_credentials, auth_base_url=webin_token_url
        )
    }
    headers = {"accept": "*/*", "Content-Type": "application/json"}
    result = requests.post(
        biosamples_url,
        headers=headers,
        params=params,
        json=reduce_isa_json_for_target_repo(
            isa_json, TargetRepository.BIOSAMPLES.value
        ).model_dump(by_alias=True, exclude_none=True),
    )

    if result.status_code != 200:
        body = (
            result.request.body.decode()
            if isinstance(result.request.body, bytes)
            else result.request.body or ""
        )
        raise requests.HTTPError(
            f"Request towards BioSamples failed!\nRequest:\nMethod:{result.request.method}\nStatus:{result.status_code}\nURL:{result.request.url}\nHeaders:{result.request.headers}\nBody:{body}"
        )

    return result


def upload_to_metabolights(
    file_paths: list[str],
    isa_json: IsaJson,
    metabolights_credentials: dict[str, str],
    metabolights_url: str,
    metabolights_token_url: str,
    file_transfer: str = "ftp",
):
    data_upload_protocol = (
        "ftp" if not file_transfer or file_transfer.lower() == "ftp" else ""
    )

    if not data_upload_protocol == "ftp":
        raise ValueError(
            f"Data upload protocol {data_upload_protocol} is not supported"
        )

    token = get_metabolights_auth_token(
        metabolights_credentials, auth_url=metabolights_token_url
    )
    headers = {
        "accept": "application/json",
        "Authorization": f"Bearer {token}",
    }
    isa_json_str = reduce_isa_json_for_target_repo(
        isa_json, TargetRepository.METABOLIGHTS
    ).investigation.model_dump_json(by_alias=True, exclude_none=True)
    json_file = io.StringIO(isa_json_str)

    files = {"isa_json_file": ("isa_json.json", json_file)}
    result = None
    try:
        submission_response = requests.post(
            metabolights_url,
            headers=headers,
            files=files,
            timeout=120,
        )
        submission_response.raise_for_status()
        result = submission_response.json()
    except Exception as exc:
        raise exc

    validation_url = find_value_in_info_section("validation-url", result["info"])
    validation_status_url = find_value_in_info_section(
        "validation-status-url", result["info"]
    )
    ftp_credentials_url = find_value_in_info_section(
        "ftp-credentials-url", result["info"]
    )

    if file_transfer == "ftp":
        ftp_credentials_response = requests.get(ftp_credentials_url, headers=headers)
        ftp_credentials_response.raise_for_status()
        ftp_credentials = ftp_credentials_response.json()
        ftp_base_path = ftp_credentials["ftpPath"]  # noqa F841
        uploader = FTPUploader(  # noqa F841
            ftp_credentials["ftpHost"],
            ftp_credentials["ftpUser"],
            ftp_credentials["ftpPassword"],
        )
        # TODO: Update after the uploader is implemented/tested
        # uploader.upload(file_paths, target_location=ftp_base_path)

    validation_response = requests.post(validation_url, headers=headers)
    validation_response.raise_for_status()
    pool_time_in_seconds = 10
    max_pool_count = 100
    validation_status_response = None
    for _ in range(max_pool_count):
        timeout = False
        try:
            validation_status_response = requests.get(
                validation_status_url, headers=headers, timeout=30
            )
            validation_status_response.raise_for_status()
        except requests.exceptions.Timeout:
            timeout = True
        if not timeout:
            if validation_status_response is None:
                raise ValueError("Validation status response is None")
            validation_status = validation_status_response.json()
            validation_time = find_value_in_info_section(
                "validation-time", validation_status["info"], fail_gracefully=True
            )
            if validation_time:
                break
        time.sleep(pool_time_in_seconds)
    else:
        raise ValueError(f"Validation failed after {max_pool_count} iterations")

    if validation_status_response:
        return validation_status_response

    raise ValueError("Submission failed for MetaboLights")


def find_value_in_info_section(
    key: str, info_section: list[Any], fail_gracefully: bool = False
) -> Any:
    for info in info_section:
        if info["name"] == key:
            return info["message"]
    if fail_gracefully:
        return None
    raise ValueError(f"Name {key} not found in info section")


def submit_to_ena(
    isa_json: IsaJson, user_credentials: dict[str, str], submission_url: str
) -> requests.Response:
    params = {
        "webinUserName": user_credentials["username"],
        "webinPassword": user_credentials["password"],
    }
    headers = {"accept": "*/*", "Content-Type": "application/json"}
    result = requests.post(
        submission_url,
        headers=headers,
        params=params,
        json=reduce_isa_json_for_target_repo(
            isa_json, TargetRepository.ENA.value
        ).model_dump(by_alias=True, exclude_none=True),
    )

    if result.status_code != 200:
        body = (
            result.request.body.decode()
            if isinstance(result.request.body, bytes)
            else result.request.body or ""
        )
        raise requests.HTTPError(
            f"Request towards ENA failed!\nRequest:\nMethod:{result.request.method}\nStatus:{result.status_code}\nURL:{submission_url}\nParams: ['webinUserName': {params.get('webinUserName')}, 'webinPassword': ****]\nHeaders:{result.request.headers}\nBody:{body}"
        )

    return result


def upload_to_ena(
    file_paths: List[Path],
    user_credentials: dict[str, str],
    submission_url: str,
    file_transfer: str,
):
    file_transfer = file_transfer.lower()

    if file_transfer == "ftp":
        uploader = FTPUploader(
            submission_url,
            user_credentials["username"],
            user_credentials["password"],
        )
        uploader.upload(file_paths)


def create_external_references(
    biosamples_credentials: dict[str, str],
    biosamples_externalReferences: dict[str, Any],
    production: bool,
) -> None:
    """
    Main function to be executed when script is run.

    Args:
    biosamples_credentials: Dictionary with the credentials of the submitter of the existing Biosamples records.
    biosamples_externalReferences: Dictionary containing the mapping between the
    production: Boolean indicating the environment of BioSamples to use.
    """
    if production:
        biosamples_endpoint = biosamples_endpoints["prod"]
    else:
        biosamples_endpoint = biosamples_endpoints["dev"]

    validate_json_against_schema(
        json_doc=biosamples_externalReferences, json_schema=input_json_schema_filepath
    )
    token = get_webin_auth_token(biosamples_credentials)
    if not token:
        raise ValueError("The token could not be generated.")
    header = get_header(token)

    for biosample_r in biosamples_externalReferences["biosampleExternalReferences"]:
        bs_accession = biosample_r["biosampleAccession"]
        BSrecord = BiosamplesRecord(bs_accession)
        BSrecord.fetch_bs_json(biosamples_endpoint)
        # To test it without the fetching, you can download it manually and then use:
        #   BSrecord.load_bs_json(bs_json_file="downloaded-json.json")
        new_ext_refs_list = biosample_r["externalReferences"]
        BSrecord.extend_externalReferences(new_ext_refs_list)
        BSrecord.update_remote_record(header)
