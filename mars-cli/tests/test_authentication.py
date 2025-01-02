import pytest
import os
from mars_lib.authentication import (
    get_webin_auth_token,
    load_credentials,
    get_metabolights_auth_token,
)
from requests.exceptions import HTTPError

fake_credentials_dict = {
    "webin": {
        "username": "my_fake_account",
        "password": "my_super_secret_password",
    },
    "metabolights_metadata": {
        "username": "my_fake_account",
        "password": "my_super_secret_password",
    },
    "metabolights_data": {
        "username": "my_fake_account",
        "password": "my_super_secret_password",
    },
}


def test_get_webin_auth_token():
    with pytest.raises(
        ValueError,
        match="ERROR when generating token. See response's content below:\nBad credentials",
    ):
        get_webin_auth_token(fake_credentials_dict["webin"])

    file = "./tests/test_credentials.json"
    if os.path.exists(file) and os.path.isfile(file):
        test_credentials = load_credentials(file)

        token = get_webin_auth_token(test_credentials["webin"])
        assert token


@pytest.mark.skipif(
    not os.path.exists("./tests/test_credentials.json"),
    reason="Credentials file not found",
)
def test_get_metabolights_auth_token():
    credentials = load_credentials("./tests/test_credentials.json")
    token = get_metabolights_auth_token(credentials["metabolights_metadata"])
    assert token

    # TODO: Currently an 'Internal Server Error' is returned when using the wrong credentials.
    # This should be updated to return a more informative error message.
    with pytest.raises(HTTPError):
        get_metabolights_auth_token(fake_credentials_dict["metabolights_metadata"])


def test_valid_credentials_file():
    # Test with a full valid credentials file (all providers)
    _max_credentials = load_credentials("tests/fixtures/max_credentials_file.json")

    # Test with a partial valid credentials file
    _min_credentials = load_credentials("tests/fixtures/min_credentials_file.json")

    # Test with a credentials file that has an invalid provider
    with pytest.raises(
        ValueError, match="Credentials dictionary must have valid keys."
    ):
        load_credentials("tests/fixtures/bad_credentials_file.json")
