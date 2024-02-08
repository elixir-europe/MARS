from mars_lib.authentication import get_webin_auth_token
from mars_lib.biosamples_externalReferences import (
    get_header,
    biosamples_endpoints,
    BiosamplesRecord,
    validate_json_against_schema,
    handle_input_dict,
)
import argparse
from argparse import RawTextHelpFormatter


def main(biosamples_credentials, biosamples_externalReferences, production):
    """
    Main function to be executed when script is run.

    Args:
    biosamples_credentials: Dictionary with the credentials of the submitter of the existing Biosamples records.
    biosamples_externalReferences: Dictionary containing the mapping between the
    production: Boolean indicating the environment of BioSamples to use.
    """
    validate_json_against_schema(
        json_doc=biosamples_externalReferences, json_schema=input_json_schema_filepath
    )
    token = get_webin_auth_token(biosamples_credentials)
    header = get_header(token)

    if production:
        biosamples_endpoint = biosamples_endpoints["prod"]
    else:
        biosamples_endpoint = biosamples_endpoints["dev"]

    for biosample_r in biosamples_externalReferences["biosampleExternalReferences"]:
        bs_accession = biosample_r["biosampleAccession"]
        BSrecord = BiosamplesRecord(bs_accession)
        BSrecord.fetch_bs_json(biosamples_endpoint)
        # To test it without the fetching, you can download it manually and then use:
        #   BSrecord.load_bs_json(bs_json_file="downloaded-json.json")
        new_ext_refs_list = biosample_r["externalReferences"]
        BSrecord.extend_externalReferences(new_ext_refs_list)
        BSrecord.update_remote_record(header)


if __name__ == "__main__":
    # Command-line argument parsing
    parser = argparse.ArgumentParser(description="Handle biosamples records.")
    description = "This script extends a set of existing Biosamples records with a list of provided external references."
    parser = argparse.ArgumentParser(
        prog="biosamples-externalReferences.py",
        description=description,
        formatter_class=RawTextHelpFormatter,
    )
    parser.add_argument(
        "biosamples_credentials",
        help="Either a dictionary or filepath to the BioSamples credentials.",
    )
    parser.add_argument(
        "biosamples_externalReferences",
        help="Either a dictionary or filepath to the BioSamples' accessions mapping with external references.",
    )
    parser.add_argument(
        "--production",
        action="store_true",
        help="Boolean indicating the usage of the production environment of BioSamples. If not present, the development instance will be used.",
    )
    # Handle inputs
    parsed_args = parser.parse_args()
    biosamples_credentials = handle_input_dict(parsed_args.biosamples_credentials)
    biosamples_externalReferences = handle_input_dict(
        parsed_args.biosamples_externalReferences
    )

    main(biosamples_credentials, biosamples_externalReferences, parsed_args.production)
