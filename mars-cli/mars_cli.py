from mars_lib.authentication import get_webin_auth_token
from mars_lib.biosamples_external_references import (
    get_header,
    biosamples_endpoints,
    BiosamplesRecord,
    validate_json_against_schema,
    handle_input_dict,
    input_json_schema_filepath,
)
import click
import logging
from mars_lib.isa_json import TargetRepository


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
        logger_name = "production"
        biosamples_endpoint = biosamples_endpoints["prod"]
    else:
        logger_name = "development"
        biosamples_endpoint = biosamples_endpoints["dev"]

        logging.basicConfig(
            filename=logger_name + ".log",
            filemode="w",
            format="%(name)s - %(levelname)s - %(message)s",
        )

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


@click.group()
@click.option(
    "--development",
    is_flag=True,
    help="Boolean indicating the usage of the development environment of the target repositories. If not present, the production instances will be used.",
)
def cli(development):
    click.echo(
        f"Running in {'Development environment' if development else 'Production environment'}"
    )


@cli.command()
@click.argument(
    "credentials_file",
    type=click.File("r"),
)
@click.argument(
    "isa_json_file",
    type=click.File("r"),
)
@click.option("--submit-to-ena", type=click.BOOL, default=True, help="Submit to ENA.")
@click.option(
    "--submit-to-metabolights",
    type=click.BOOL,
    default=True,
    help="Submit to Metabolights.",
)
def submit(credentials_file, isa_json_file, submit_to_ena, submit_to_metabolights):
    target_repositories = ["biosamples"]
    if submit_to_ena:
        target_repositories.append(TargetRepository.ENA)

    if submit_to_metabolights:
        target_repositories.append(TargetRepository.METABOLIGHTS)

    click.echo(
        f"Staring submission of the ISA JSON to the target repositories: {', '.join(target_repositories)}."
    )


@cli.command()
def health_check():
    click.echo("Checking the health of the target repositories.")


if __name__ == "__main__":
    cli()
