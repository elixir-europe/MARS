import requests
import json


def get_webin_auth_token(
    credentials_dict,
    header={"Content-Type": "application/json"},
    auth_url="https://wwwdev.ebi.ac.uk/ena/submit/webin/auth/token",
):
    """
    Obtain Webin authentication token.

    Args:
    credentials_dict (dict): The password dictionary for authentication.
    header (dict): The header information.
    auth_url (str): The URL for authentication.

    Returns:
    str: The obtained token.
    """
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
