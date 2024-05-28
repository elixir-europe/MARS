from mars_lib.models.isa_json import Investigation
from mars_lib.models.repository_response import RepositoryResponse
from mars_lib.authentication import get_webin_auth_token
from mars_lib.biosamples_external_references import (
    get_header,
    biosamples_endpoints,
    BiosamplesRecord,
    validate_json_against_schema,
    input_json_schema_filepath,
)
from mars_lib.isa_json import reduce_isa_json_for_target_repo
from mars_lib.target_repo import TargetRepository
from mars_lib.authentication import get_webin_auth_token
import requests


def submit_to_biosamples(
    investiagation: Investigation,
    biosamples_credentials,
    url,
) -> requests.Response:
    bs_input_investiagation = reduce_isa_json_for_target_repo(
        investiagation, TargetRepository.BIOSAMPLES
    )

    webin_token = get_webin_auth_token(biosamples_credentials)
    s = requests.Session()
    s.headers.update({"accept": "application/json", "Content-Type": "application/json"})
    return s.post(
        url,
        params={"webinjwt": webin_token},
        data=bs_input_investiagation.model_dump_json(),
    )


def create_external_references(
    biosamples_credentials, biosamples_externalReferences, production
):
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
