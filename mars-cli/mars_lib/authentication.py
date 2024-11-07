from typing import Optional
import requests
import json


def get_webin_auth_token(
    credentials_dict: dict[str, str],
    header: dict[str, str] = {"Content-Type": "application/json"},
    auth_base_url: str = "https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth/token",
    token_expiration_time: int = 1,
) -> Optional[str]:
    """
    Obtain Webin authentication token.

    Args:
    credentials_dict (dict): The password dictionary for authentication.
    header (dict): The header information.
    auth_base_url (str): The base URL for authentication.
    token_expiration_time(int): Token expiration time in hours.

    Returns:
    str: The obtained token.
    """
    auth_url = f"{auth_base_url}?ttl={token_expiration_time}"
    data = json.dumps(
        {
            "authRealms": ["ENA"],
            "password": credentials_dict["password"],
            "username": credentials_dict["username"],
        }
    )
    try:
        response = requests.post(auth_url, headers=header, data=data)
        token = response.content.decode("utf-8")
    except Exception as e:
        raise e

    if response.status_code != 200:
        response_content = response.content.decode("utf-8")
        error_message = f"ERROR when generating token. See response's content below:\n{response_content}"
        raise ValueError(error_message)

    return token


def get_metabolights_auth_token(
    credentials_dict: dict[str, str],
    headers: dict[str, str] = {"Content-Type": "application/json"},
    auth_url: str = "https://www-test.ebi.ac.uk/metabolights/mars/auth/token",
) -> Optional[str]:
    """
    Obtain Webin authentication token.

    Args:
    credentials_dict (dict): The password dictionary for authentication.
    header (dict): The header information.
    auth__url (str): The URL for MetaboLights authentication.

    Returns:
    str: The obtained token.
    """
    try:
        response = requests.post(
            auth_url,
            headers=headers,
            json={"username": credentials_dict["username"], "password": credentials_dict["password"]},
            timeout=5,
        )
        response.raise_for_status()

    except Exception as ex:
        raise ex
    
    response_content = response.content.decode("utf-8")
    if response and "Jwt" in response.headers and response.headers["Jwt"]:
        return response.headers["Jwt"]
    else:
        error_message = f"ERROR when generating token. See response's content below:\n{response_content}"
        raise Exception(error_message)