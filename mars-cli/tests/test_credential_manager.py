import pytest

from mars_lib.credential import CredentialManager


def test_create_credentials_manager():
    cm = CredentialManager("webin")
    assert cm is not None

    with pytest.raises(
        ValueError, match="Invalid authentication provider: invalid_provider"
    ):
        CredentialManager("invalid_provider")


def test_set_password_keyring():
    cm = CredentialManager("mars-cli")
    cm.set_password_keyring("username", "password")
    assert cm.get_password_keyring("username") == "password"
