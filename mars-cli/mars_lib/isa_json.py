import json
from typing import Dict, Union, List
from mars_lib.model import Investigation, Assay, Comment
from pydantic import ValidationError
from mars_lib.target_repo import TARGET_REPO_KEY


def reduce_isa_json_for_target_repo(
    input_isa_json: Investigation, target_repo: str
) -> Investigation:
    """
    Filters out assays that are not meant to be sent to the specified target repository.

    Args:
        input_isa_json (Investigation): Input ISA JSON that contains the original information.
        target_repo (TargetRepository): Target repository as a constant.

    Returns:
        Investigation: Filtered ISA JSON.
    """
    filtered_isa_json = input_isa_json.model_copy(deep=True)
    new_studies = []
    studies = filtered_isa_json.studies
    for study in studies:
        assays = study.assays
        filtered_assays = [
            assay for assay in assays if is_assay_for_target_repo(assay, target_repo)
        ]
        if len(filtered_assays) > 0:
            study.assays = filtered_assays
            new_studies.append(study)

    filtered_isa_json.studies = new_studies
    return filtered_isa_json


def detect_target_repo_comment(comments: List[Comment]) -> Comment:
    """Will detect the comment that contains the target repository.

    Args:
        comments (List[Comment]): List of comments.

    Returns:
        Comment: The comment where the name corresponds with the name of the provided target repo.
    """
    for comment in comments:
        if comment.name == TARGET_REPO_KEY:
            return comment


def is_assay_for_target_repo(assay: Assay, target_repo: str) -> bool:
    """
    Defines whether the assays is meant for the target repository.

    Args:
        assay_dict (Dict[str, str]): Dictionary representation of an assay.
        target_repo (TargetRepository): Target repository as a constant.

    Returns:
        bool: Boolean defining whether the assay is destined for the provided target repo.
    """
    target_repo_comment = detect_target_repo_comment(assay.comments)
    if target_repo_comment.value == target_repo:
        return True
    else:
        return False


def load_isa_json(file_path: str) -> Union[Dict[str, str], ValidationError]:
    """
    Reads the file and validates it as a valid ISA JSON.

    Args:
        file_path (str): Path to ISA JSON as string.

    Returns:
        Union[Dict[str, str], ValidationError]: Depending on the validation, returns a filtered ISA JSON or a pydantic validation error.
    """
    with open(file_path, "r") as json_file:
        isa_json = json.load(json_file)

    # Validation of the ISA JSON
    return Investigation.model_validate(isa_json)
