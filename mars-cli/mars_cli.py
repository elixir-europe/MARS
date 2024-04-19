import click
import logging
import pathlib
from configparser import ConfigParser
from mars_lib.target_repo import TargetRepository
from mars_lib.model import Investigation, IsaJson
from logging.handlers import RotatingFileHandler
import requests
import sys
import os
import json

# Load CLI configuration
home_dir = (
    pathlib.Path(os.getenv("MARS_SETTINGS_DIR"))
    if os.getenv("MARS_SETTINGS_DIR")
    else pathlib.Path.home()
)

config_file = home_dir / ".mars" / "settings.ini"
fallback_log_file = home_dir / ".mars" / "app.log"

config = ConfigParser()
config.read(config_file)

# Logging configuration
log_level = config.get("logging", "log_level", fallback="ERROR")
log_file = config.get("logging", "log_file", fallback=fallback_log_file)
log_max_size = int(
    config.get("logging", "log_max_size", fallback="1024")
)  # in kilobytes. 1 MB by default.
log_max_files = int(
    config.get("logging", "log_max_files", fallback="5")
)  # number of backup files. 5 by default.

handler = RotatingFileHandler(
    log_file, maxBytes=log_max_size * 1024, backupCount=log_max_files
)
handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))

logging.basicConfig(
    handlers=[handler],
    level=log_level,
)


def print_and_log(msg, level="info"):
    if level == "info":
        click.echo(msg)
        logging.info(msg)
    elif level == "error":
        click.echo(msg, file=sys.stderr)
        logging.error(msg)
    elif level == "warning":
        click.echo(msg)
        logging.warning(msg)
    else:
        click.echo(msg)
        logging.debug(msg)


@click.group()
@click.option(
    "--development",
    "-d",
    is_flag=True,
    help="Boolean indicating the usage of the development environment of the target repositories. If not present, the production instances will be used.",
)
@click.pass_context
def cli(ctx, development):
    print_and_log("############# Welcome to the MARS CLI. #############")
    print_and_log(
        f"Running in {'Development environment' if development else 'Production environment'}"
    )

    ctx.ensure_object(dict)
    ctx.obj["DEVELOPMENT"] = development


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
    """Start a submission to the target repositories."""
    target_repositories = ["biosamples"]
    if submit_to_ena:
        target_repositories.append(TargetRepository.ENA)

    if submit_to_metabolights:
        target_repositories.append(TargetRepository.METABOLIGHTS)

    print_and_log(
        f"Staring submission of the ISA JSON to the target repositories: {', '.join(target_repositories)}."
    )

    # TODO: Entry point for the submission logic


@cli.command()
@click.pass_context
def health_check(ctx):
    """Check the health of the target repositories."""
    print_and_log("Checking the health of the target repositories.")

    if ctx.obj["DEVELOPMENT"]:
        print_and_log("Checking development instances.")
        webin_url = config.get("webin", "development-url")
        ena_url = config.get("ena", "development-url")
        biosamples_url = config.get("biosamples", "development-url")
    else:
        print_and_log("Checking production instances.")
        webin_url = config.get("webin", "production-url")
        ena_url = config.get("ena", "production-url")
        biosamples_url = config.get("biosamples", "production-url")

    # Check webin service
    webin_health = requests.get(webin_url)
    if webin_health.status_code != 200:
        print_and_log(
            f"Webin ({webin_url}): Could not reach service! Status code '{webin_health.status_code}'.",
            level="error",
        )
    else:
        print_and_log(f"Webin ({webin_url}) is healthy.")

    # Check ENA service
    ena_health = requests.get(ena_url)
    if ena_health.status_code != 200:
        print_and_log(
            f"ENA ({ena_url}): Could not reach service! Status code '{ena_health.status_code}'.",
            level="error",
        )
    else:
        print_and_log(f"ENA ({ena_url}) is healthy.")

    # Check Biosamples service
    biosamples_health = requests.get(biosamples_url)
    if biosamples_health.status_code != 200:
        print_and_log(
            f"Biosamples ({biosamples_url}): Could not reach service! Status code '{biosamples_health.status_code}'.",
            level="error",
        )
    else:
        print_and_log(f"Biosamples ({biosamples_url}) is healthy.")


@cli.command()
@click.argument(
    "isa_json_file",
    type=click.Path(exists=True),
)
@click.option(
    "--investigation-is-root",
    default=False,
    type=click.BOOL,
    help="Boolean indicating if the investigation is the root of the ISA JSON. Set this to True if the ISA-JSON does not contain a 'investigation' field.",
)
def validate_isa_json(isa_json_file, investigation_is_root):
    """Validate the ISA JSON file."""
    print_and_log(f"Validating {isa_json_file}.")

    with open(isa_json_file) as f:
        json_data = json.load(f)

    if investigation_is_root:
        investigation = Investigation.model_validate(json_data)
    else:
        investigation = IsaJson.model_validate(json_data).investigation

    print_and_log(f"ISA JSON with investigation '{investigation.title}' is valid.")

if __name__ == "__main__":
    cli()
