import keyring
import os
import getpass

"""
Credential Manager Module
=========================

This module provides a secure and flexible way to manage credentials for your Python applications. It supports retrieving credentials from environment variables, securely prompting for passwords in the console, and managing passwords using the system's keyring service.

Usage:
------

1. Environment Variables:
   - Use `get_credential_env(key)` to retrieve a credential stored in an environment variable.

2. Console Password Input:
   - Use `prompt_for_password()` to securely prompt the user to enter a password in the console.

3. Keyring Management:
   - Use `set_password_keyring(username, password)` to store a password in the keyring.
   - Use `get_password_keyring(username)` to retrieve a password from the keyring.
   - Use `delete_password_keyring(username)` to delete a password from the keyring.

Example:
--------

from credential import CredentialManager

# Initialize the Credential Manager with your service name
cred_manager = CredentialManager("MARS")

# Retrieve a credential from environment variables
api_key = cred_manager.get_credential_env("username")

# Prompt for a password
password = cred_manager.prompt_for_password()

# Store and retrieve a password using the keyring
cred_manager.set_password_keyring("username", "password")
retrieved_password = cred_manager.get_password_keyring("username")

# Don't forget to handle exceptions and secure your credentials properly.
"""
import keyring.util.platform_ as keyring_platform

print(keyring_platform.config_root())
# /home/username/.config/python_keyring  # Might be different for you

print(keyring.get_keyring())
# keyring.backends.SecretService.Keyring (priority: 5)


class CredentialManager:
    def __init__(self, service_name):
        self.service_name = service_name

    def get_credential_env(self, username):
        """
        Retrieves a credential from environment variables.

        :param username: The environment variable username.
        :return: The value of the environment variable or None if not found.
        """
        return os.getenv(username)

    def prompt_for_password(self):
        """
        Securely prompts the user to enter a password in the console.

        :return: The password entered by the user.
        """
        return getpass.getpass(prompt="Enter your password: ")

    def set_password_keyring(self, username, password):
        """
        Stores a password in the keyring under the given username.

        :param username: The username associated with the password.
        :param password: The password to store.
        """
        keyring.set_password(self.service_name, username, password)

    def get_password_keyring(self, username):
        """
        Retrieves a password from the keyring for the given username.

        :param username: The username whose password to retrieve.
        :return: The password or None if not found.
        """
        return keyring.get_password(self.service_name, username)

    def delete_password_keyring(self, username):
        """
        Deletes a password from the keyring for the given username.

        :param username: The username whose password to delete.
        """
        keyring.delete_password(self.service_name, username)
