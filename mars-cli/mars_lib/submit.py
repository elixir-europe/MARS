import os
from datetime import datetime
from io import TextIOWrapper
import requests
import json
from typing import Any
from mars_lib.authentication import get_webin_auth_token
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
)
from mars_lib.models.isa_json import IsaJson
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
    credential_service_name: str,
    username_credentials: str,
    credentials_file: TextIOWrapper,
    isa_json_file: str,
    target_repositories: list[str],
    investigation_is_root: bool,
    urls: dict[str, Any],
    file_transfer: str,
    output: str,
    data_file_paths=None,
) -> None:
    # If credential manager info found:
    # Get password from the credential manager
    # Else:
    # read credentials from file
    if not (credential_service_name is None or username_credentials is None):
        cm = CredentialManager(credential_service_name)
        user_credentials = {
            "username": username_credentials,
            "password": cm.get_password_keyring(username_credentials),
        }
    else:
        if credentials_file == "":
            raise ValueError("No credentials found")

        user_credentials = json.load(credentials_file)

    isa_json = load_isa_json(isa_json_file, investigation_is_root)

    # Guard clause to keep MyPy happy
    if isinstance(isa_json, ValidationError):
        raise ValidationError(f"ISA JSON is invalid: {isa_json}")

    print_and_log(
        f"ISA JSON with investigation '{isa_json.investigation.title}' is valid."
    )

    time_stamp = datetime.timestamp(datetime.now())

    if DEBUG:
        save_step_to_file(time_stamp, "0_Initial_ISA_JSON_in_model", isa_json)

    if all(
        repo not in TargetRepository.available_repositories()
        for repo in target_repositories
    ):
        raise ValueError("No target repository selected.")

    if TargetRepository.BIOSAMPLES in target_repositories:
        # Submit to Biosamples
        biosamples_result = submit_to_biosamples(
            isa_json=isa_json,
            biosamples_credentials=user_credentials,
            biosamples_url=urls["BIOSAMPLES"]["SUBMISSION"],
            webin_token_url=urls["WEBIN"]["TOKEN"],
        )
        print_and_log(
            f"Submission to {TargetRepository.BIOSAMPLES} was successful. Result:\n{biosamples_result.json()}",
            level="info",
        )
        # Update `isa_json`, based on the receipt returned
        bs_mars_receipt = RepositoryResponse.model_validate(
            json.loads(biosamples_result.content)
        )
        isa_json = update_isa_json(isa_json, bs_mars_receipt)
        if DEBUG:
            save_step_to_file(time_stamp, "1_after_biosamples", isa_json)

    if TargetRepository.ENA in target_repositories:
        # Step 1 : upload data if file paths are provided
        if data_file_paths and file_transfer:
            upload_to_ena(
                file_paths=data_file_paths,
                user_credentials=user_credentials,
                submission_url=urls["ENA"]["DATA-SUBMISSION"],
                file_transfer=file_transfer,
            )
        print_and_log(
            f"Start submitting to {TargetRepository.ENA}.", level='debug'
        )

        # Step 2 : submit isa-json to ena
        ena_result = submit_to_ena(
            isa_json=isa_json,
            user_credentials=user_credentials,
            submission_url=urls["ENA"]["SUBMISSION"],
        )
        print_and_log(
            f"Submission to {TargetRepository.ENA} was successful. Result:\n{ena_result.json()}"
        )

        print_and_log(
            f"Update ISA-JSON based on receipt from {TargetRepository.ENA}.", level='debug'
        )
        ena_mars_receipt = RepositoryResponse.model_validate(
            json.loads(ena_result.content)
        )
        isa_json = update_isa_json(isa_json, ena_mars_receipt)
        if DEBUG:
            save_step_to_file(time_stamp, "2_after_ena", isa_json)

    if TargetRepository.METABOLIGHTS in target_repositories:
        # Submit to MetaboLights
        # TODO: Filter out other assays
        print_and_log(
            f"Submission to {TargetRepository.METABOLIGHTS} was successful",
            level="info",
        )
        # TODO: Update `isa_json`, based on the receipt returned

    if TargetRepository.EVA in target_repositories:
        # Submit to EVA
        # TODO: Filter out other assays
        print_and_log(
            f"Submission to {TargetRepository.EVA} was successful", level="info"
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
            isa_json, TargetRepository.BIOSAMPLES
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
        json=reduce_isa_json_for_target_repo(isa_json, TargetRepository.ENA).model_dump(
            by_alias=True, exclude_none=True
        ),
    )

    if result.status_code != 200:
        body = (
            result.request.body.decode()
            if isinstance(result.request.body, bytes)
            else result.request.body or ""
        )
        raise requests.HTTPError(
            f"Request towards ENA failed!\nRequest:\nMethod:{result.request.method}\nStatus:{result.status_code}\nURL:{result.request.url}\nHeaders:{result.request.headers}\nBody:{body}"
        )

    return result


def upload_to_ena(
    file_paths: List[Path],
    user_credentials: dict[str, str],
    submission_url: str,
    file_transfer: str,
):
    ALLOWED_FILE_TRANSFER_SOLUTIONS = {"ftp", "aspera"}
    file_transfer = file_transfer.lower()

    if file_transfer not in ALLOWED_FILE_TRANSFER_SOLUTIONS:
        raise ValueError(f"Unsupported transfer protocol: {file_transfer}")
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
