import pytest
from mars_lib.authentication import get_webin_auth_token


def test_get_webin_auth_token():
    credentials_dict = {
        "username": "my_fake_account",
        "password": "my_super_secret_password",
    }
    with pytest.raises(
        ValueError,
        match="ERROR when generating token. See response's content below:\nBad credentials",
    ):
        get_webin_auth_token(credentials_dict)
