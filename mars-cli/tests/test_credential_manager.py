import pytest
from mars_lib.credential import CredentialManager


def test_create_credentials_manager():
    cm = CredentialManager("mars-cli")
    assert cm is not None


def test_set_password_keyring():
    cm = CredentialManager("mars-cli")
    cm.set_password_keyring("username", "password")
    assert cm.get_password_keyring("username") == "password"
