import pytest
import json
import os
from mars_lib.authentication import get_webin_auth_token


def test_get_webin_auth_token():
    fake_credentials_dict = {
        "username": "my_fake_account",
        "password": "my_super_secret_password",
    }
    with pytest.raises(
        ValueError,
        match="ERROR when generating token. See response's content below:\nBad credentials",
    ):
        get_webin_auth_token(fake_credentials_dict)

    file = "./tests/test_credentials.json"
    if os.path.exists(file) and os.path.isfile(file):
        with open(file, "r") as f:
            test_credentials = json.load(f)

        token = get_webin_auth_token(test_credentials)
        assert token
