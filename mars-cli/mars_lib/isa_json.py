import json
from typing import Dict, Union, List
import copy

TARGET_REPO_KEY = "target repository"


class IsaJsonValidationError(ValueError):
    """
    Custom Error object to be used when the validation fails.
    This class extends the ValueError class.
    """

    def __init__(self, report, message="The Provided ISA JSON is invalid!"):
        self.message = message + "\n" + str(report["errors"])
        super().__init__(self.message)


class TargetRepository:
    """
    Holds constants, tied to the target repositories.
    """

    ENA = "ena"
    METABOLIGHTS = "metabolights"
    BIOSAMPLES = "biosamples"


def reduce_isa_json_for_target_repo(
    input_isa_json: Dict, target_repo: str
) -> Dict[str, str]:
    """
    Filters out assays that are not meant to be sent to the specified target repository.

    Args:
        input_isa_json (Dict[str, str]): Input ISA JSON that contains the original information.
        target_repo (TargetRepository): Target repository as a constant.

    Returns:
        Dict[str, str]: Filtered ISA JSON.
    """
    filtered_isa_json = copy.deepcopy(input_isa_json)
    new_studies = []
    studies = filtered_isa_json.pop("studies")
    for study in studies:
        assays = study.pop("assays")
        filtered_assays = [
            assay for assay in assays if is_assay_for_target_repo(assay, target_repo)
        ]
        if len(filtered_assays) > 0:
            study["assays"] = filtered_assays
            new_studies.append(study)

    filtered_isa_json["studies"] = new_studies
    return filtered_isa_json


def detect_target_repo_comment(comments: List[Dict[str, str]]) -> Dict[str, str]:
    """_summary_

    Args:
        comments (List[Dict[str, str]]): Dictionary of comments.

    Returns:
        Dict[str, str]: The comment where the name corresponds with the name of the provided target repo.
    """
    for comment in comments:
        if comment["name"] == TARGET_REPO_KEY:
            return comment


def is_assay_for_target_repo(assay_dict: Dict, target_repo: str) -> bool:
    """
    Defines whether the assays is meant for the target repository.

    Args:
        assay_dict (Dict[str, str]): Dictionary representation of an assay.
        target_repo (TargetRepository): Target repository as a constant.

    Returns:
        bool: Boolean defining whether the assay is destined for the provided target repo.
    """
    target_repo_comment = detect_target_repo_comment(assay_dict["comments"])
    if target_repo_comment["value"] == target_repo:
        return True
    else:
        return False


def load_isa_json(file_path: str) -> Union[Dict[str, str], IsaJsonValidationError]:
    """
    Reads the file and validates it as a valid ISA JSON.

    Args:
        file_path (str): Path to ISA JSON as string.

    Returns:
        Union[Dict[str, str], IsaJsonValidationError]: Depending on the validation, returns a filtered ISA JSON or an Error.
    """
    with open(file_path, "r") as json_file:
        isa_json = json.load(json_file)

    # TODO: Once we have an idea on what / how to validate, it should be added here

    return isa_json
