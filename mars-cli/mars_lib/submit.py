import requests
from typing import Any, Union
from mars_lib.authentication import get_webin_auth_token
from mars_lib.biosamples_external_references import (
    get_header,
    biosamples_endpoints,
    BiosamplesRecord,
    validate_json_against_schema,
    input_json_schema_filepath,
)
from mars_lib.credential import CredentialManager
from mars_lib.isa_json import load_isa_json
from mars_lib.models.isa_json import IsaJson, Investigation
from mars_lib.target_repo import TargetRepository
from mars_lib.logging import print_and_log


def submission(
    credential_service_name,
    username_credentials,
    isa_json_file,
    target_repositories,
    investigation_is_root,
    urls,
):
    # Get password from the credential manager
    cm = CredentialManager(credential_service_name)
    user_credentials = {
        "username": username_credentials,
        "password": cm.get_password_keyring(username_credentials),
    }

    isa_json: IsaJson = load_isa_json(isa_json_file, investigation_is_root)

    # Remove the biosamples step if ENA is the repositories list
    # Probably not the best way to address this
    if TargetRepository.ENA in target_repositories:
        target_repositories.remove(TargetRepository.BIOSAMPLES)
        print_and_log(
            f"Skipping {TargetRepository.BIOSAMPLES} repository due to {TargetRepository.ENA} being present in the list of repositories",
            level="debug",
        )

    if TargetRepository.ENA in target_repositories:
        submit_to_ena(
            isa_json=isa_json,
            user_credentials=user_credentials,
            submission_url=urls["ENA"]["SUBMISSION"],
        )
        print_and_log(f"Submission to {TargetRepository.ENA} was successful")
        # TODO: Update `isa_json`, based on the receipt returned
    elif TargetRepository.BIOSAMPLES in target_repositories:
        # Submit to Biosamples
        submit_to_biosamples(
            isa_json=isa_json,
            biosamples_credentials=user_credentials,
            biosamples_url=urls["BIOSAMPLES"]["SUBMISSION"],
            webin_token_url=urls["WEBIN"]["TOKEN"],
        )
        print_and_log(
            f"Submission to {TargetRepository.BIOSAMPLES} was successful", level="info"
        )
        # TODO: Update `isa_json`, based on the receipt returned
    elif TargetRepository.METABOLIGHTS in target_repositories:
        # Submit to MetaboLights
        print_and_log(
            f"Submission to {TargetRepository.METABOLIGHTS} was successful",
            level="info",
        )
        # TODO: Update `isa_json`, based on the receipt returned
    elif TargetRepository.EVA in target_repositories:
        # Submit to EVA
        print_and_log(
            f"Submission to {TargetRepository.EVA} was successful", level="info"
        )
        # TODO: Update `isa_json`, based on the receipt returned
    else:
        raise ValueError("No target repository selected.")


def submit_to_biosamples(
    isa_json: IsaJson,
    biosamples_credentials: dict[str, str],
    webin_token_url: str,
    biosamples_url: str,
) -> Union[requests.Response, requests.HTTPError]:
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
        json=isa_json.model_dump(by_alias=True, exclude_none=True),
    )

    if result.status_code != 200:
        raise requests.HTTPError(
            f"Request towards BioSamples failed!\nRequest:\nMethod:{result.request.method}\nStatus:{result.status_code}\nURL:{result.request.url}\nHeaders:{result.request.headers}\nBody:{result.request.body}"
        )

    return result


def submit_to_ena(
    isa_json: IsaJson, user_credentials: dict[str, str], submission_url: str
) -> Union[requests.Response, requests.RequestException]:
    params = {
        "webinUserName": user_credentials["username"],
        "webinPassword": user_credentials["password"],
    }
    headers = {"accept": "*/*", "Content-Type": "application/json"}
    result = requests.post(
        submission_url,
        headers=headers,
        params=params,
        json=isa_json.model_dump(by_alias=True, exclude_none=True),
    )

    if result.status_code != 200:
        raise requests.HTTPError(
            f"Request towards ENA failed!\nRequest:\nMethod:{result.request.method}\nStatus:{result.status_code}\nURL:{result.request.url}\nHeaders:{result.request.headers}\nBody:{result.request.body}"
        )

    return result


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
