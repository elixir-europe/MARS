import click
import logging
import pathlib
from configparser import ConfigParser
from datetime import datetime
from mars_lib.target_repo import TargetRepository
from mars_lib.models.isa_json import Investigation, IsaJson
from mars_lib.submit import submission
from mars_lib.credential import CredentialManager
from mars_lib.logging import print_and_log
from mars_lib.validation import validate, CustomValidationException
from logging.handlers import RotatingFileHandler
from pydantic import ValidationError
import requests
import sys
import os
import json

# Load CLI configuration
home_dir = (
    pathlib.Path(str(os.getenv("MARS_SETTINGS_DIR")))
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

# Read in all the URLs from the config file
urls = {
    "DEV": {
        "ENA": {
            "SERVICE": config.get(
                "ena",
                "development-url",
                fallback="https://wwwdev.ebi.ac.uk/biosamples/samples",
            ),
            "SUBMISSION": config.get(
                "ena",
                "development-submission-url",
                fallback="https://wwwdev.ebi.ac.uk/biosamples/samples/submit",
            ),
        },
        "WEBIN": {
            "SERVICE": config.get(
                "webin",
                "development-url",
                fallback="https://wwwdev.ebi.ac.uk/ena/submit/webin/auth",
            ),
            "TOKEN": config.get(
                "webin",
                "development-token-url",
                fallback="https://wwwdev.ebi.ac.uk/ena/submit/webin/auth/token",
            ),
        },
        "BIOSAMPLES": {
            "SERVICE": config.get(
                "biosamples",
                "development-url",
                fallback="https://wwwdev.ebi.ac.uk/biosamples/samples/",
            ),
            "SUBMISSION": config.get(
                "biosamples",
                "development-submission-url",
                fallback="https://wwwdev.ebi.ac.uk/biosamples/samples/",
            ),
        },
    },
    "PROD": {
        "ENA": {
            "SERVICE": config.get(
                "ena",
                "production-url",
                fallback="https://www.ebi.ac.uk/ena/submit/webin-v2/",
            ),
            "SUBMISSION": config.get(
                "ena",
                "production-submission-url",
                fallback="https://www.ebi.ac.uk/ena/submit/drop-box/submit/?auth=ENA",
            ),
        },
        "WEBIN": {
            "SERVICE": config.get(
                "webin",
                "production-url",
                fallback="https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth",
            ),
            "TOKEN": config.get(
                "webin",
                "production-token-url",
                fallback="https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth/token",
            ),
        },
        "BIOSAMPLES": {
            "SERVICE": config.get(
                "biosamples",
                "production-url",
                fallback="https://www.ebi.ac.uk/biosamples/samples/",
            ),
            "SUBMISSION": config.get(
                "biosamples",
                "production-submission-url",
                fallback="https://www.ebi.ac.uk/biosamples/samples/",
            ),
        },
    },
}


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
    if development:
        ctx.obj["FILTERED_URLS"] = urls["DEV"]
    else:
        ctx.obj["FILTERED_URLS"] = urls["PROD"]


@cli.command()
@click.option(
    "--credential-service-name", type=click.STRING, help="service name from the keyring"
)
@click.option(
    "--username-credentials", type=click.STRING, help="Username from the keyring"
)
@click.option(
    "--credentials-file",
    type=click.File("r"),
    required=False,
    help="Name of a credentials file",
)
@click.argument("isa_json_file", type=click.File("r"))
@click.option("--submit-to-ena", type=click.BOOL, default=True, help="Submit to ENA.")
@click.option(
    "--submit-to-metabolights",
    type=click.BOOL,
    default=True,
    help="Submit to Metabolights.",
)
@click.option(
    "--investigation-is-root",
    default=False,
    type=click.BOOL,
    help="Boolean indicating if the investigation is the root of the ISA JSON. Set this to True if the ISA-JSON does not contain a 'investigation' field.",
)
@click.pass_context
def submit(
    ctx,
    credential_service_name,
    username_credentials,
    credentials_file,
    isa_json_file,
    submit_to_ena,
    submit_to_metabolights,
    investigation_is_root,
):
    """Start a submission to the target repositories."""
    target_repositories = [TargetRepository.BIOSAMPLES]

    if submit_to_ena:
        target_repositories.append(TargetRepository.ENA)
        target_repositories.remove(TargetRepository.BIOSAMPLES)
        print_and_log(
            f"Skipping {TargetRepository.BIOSAMPLES} repository due to {TargetRepository.ENA} being present in the list of repositories",
            level="debug",
        )

    if submit_to_metabolights:
        target_repositories.append(TargetRepository.METABOLIGHTS)

    print_and_log(
        f"Staring submission of the ISA JSON to the target repositories: {', '.join(target_repositories)}."
    )

    urls_dict = ctx.obj["FILTERED_URLS"]
    try:
        submission(
            credential_service_name,
            username_credentials,
            credentials_file,
            isa_json_file.name,
            target_repositories,
            investigation_is_root,
            urls_dict,
        )
    except requests.RequestException as err:
        tb = sys.exc_info()[2]  # Traceback value
        print_and_log(
            f"Request to repository could not be made.\n{err.with_traceback(tb)}",
            level="error",
        )

    except ValidationError as err:
        tb = sys.exc_info()[2]  # Traceback value
        print_and_log(
            f"A validation error occurred while reading the ISA JSON. Please correct the following mistakes:\n{err.with_traceback(tb)}",
            level="error",
        )

    except Exception as err:
        tb = sys.exc_info()[2]  # Traceback value
        print_and_log(
            f"Unexpected error occurred during submission.\n{err.with_traceback(tb)}",
            level="error",
        )


@cli.command()
@click.pass_context
def health_check(ctx):
    """Check the health of the target repositories."""
    print_and_log("Checking the health of the target repositories.")

    filtered_urls = ctx.obj["FILTERED_URLS"]
    for repo in ["WEBIN", "ENA", "BIOSAMPLES"]:
        repo_url = filtered_urls[repo]["SERVICE"]
        try:
            health_response = requests.get(repo_url)
            if health_response.status_code != 200:
                print_and_log(
                    f"Could not reach service '{repo.lower()}' on this URL: '{repo_url}'. Status code: {health_response.status_code}. Content: {health_response.json()}",
                    level="error",
                )
            else:
                print_and_log(f"Service '{repo.lower()}' healthy and ready to use!")

        except requests.RequestException as err:
            tb = sys.exc_info()[2]
            print_and_log(
                f"Unexpected error for service '{repo.lower()}' on this URL: '{repo_url}'.\nError Trace:\n{err.with_traceback(tb)}",
                level="error",
            )
        except Exception as err:
            tb = sys.exc_info()[2]
            print_and_log(
                f"Unexpected error occurred.\nError Trace:\n{err.with_traceback(tb)}",
                level="error",
            )


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
@click.option("--validation-schema", default="{}", type=click.STRING, help="")
def validate_isa_json(isa_json_file, investigation_is_root, validation_schema):
    """Validate the ISA JSON file."""
    print_and_log(f"Validating {isa_json_file}.")

    try:
        with open(isa_json_file) as f:
            json_data = json.load(f)

        if investigation_is_root:
            isa_json = IsaJson(investigation=isa_json_file.model_validate(json_data))
        else:
            isa_json = IsaJson.model_validate(json_data).investigation
        validation_schema = json.loads(validation_schema)
        isa_json = validate(isa_json, validation_schema)
        print_and_log(f"ISA JSON with investigation '{isa_json.title}' is valid.")
    except CustomValidationException as err:
        print_and_log(f"Validation errors occurred:\n{err}", level="error")
    except Exception as err:
        print_and_log(f"Unexpected error occurred:\n{err}", level="error")


@cli.command()
@click.option(
    "--service-name",
    type=click.STRING,
    is_flag=False,
    flag_value="value",
    default=f"mars-cli_{datetime.now().strftime('%Y-%m-%dT%H:%M:%S')}",
    help='You are advised to include service name to match the credentials to. If empty, it defaults to "mars-cli_{DATESTAMP}"',
)
@click.argument(
    "username",
    type=click.STRING,
)
@click.option(
    "--password",
    type=click.STRING,
    hide_input=True,
    prompt=True,
    confirmation_prompt=True,
    help="The password to store. Note: You are required to confirm the password.",
)
def set_password(service_name, username, password):
    """Store a password in the keyring."""
    CredentialManager(service_name).set_password_keyring(username, password)


if __name__ == "__main__":
    cli()
