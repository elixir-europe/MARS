from math import log
import click
import logging
import pathlib
from configparser import ConfigParser
from mars_lib.isa_json import TargetRepository
from logging.handlers import RotatingFileHandler

# Load CLI configuration
home_dir = pathlib.Path.home()
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


def print_and_log(msg):
    click.echo(msg)
    logging.info(msg)


@click.group()
@click.option(
    "--development",
    is_flag=True,
    help="Boolean indicating the usage of the development environment of the target repositories. If not present, the production instances will be used.",
)
def cli(development):
    print_and_log(
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
    """Start a submission to the target repositories."""
    target_repositories = ["biosamples"]
    if submit_to_ena:
        target_repositories.append(TargetRepository.ENA)

    if submit_to_metabolights:
        target_repositories.append(TargetRepository.METABOLIGHTS)

    print_and_log(
        f"Staring submission of the ISA JSON to the target repositories: {', '.join(target_repositories)}."
    )


@cli.command()
def health_check():
    """Check the health of the target repositories."""
    print_and_log("Checking the health of the target repositories.")


if __name__ == "__main__":
    cli()
