import json
from typing import Dict, Union
import copy

TARGET_REPO_KEY = "target repository"


class IsaJsonValidationError(ValueError):
    def __init__(self, report, message="The Provided ISA JSON is invalid!"):
        self.message = message + "\n" + str(report["errors"])
        super().__init__(self.message)


class TargetRepository:
    ENA = "ena"
    METABOLIGHTS = "metabolights"
    BIOSAMPLES = "biosamples"


def reduce_isa_json_for_target_repo(
    input_isa_json: Dict[str, str], target_repo: TargetRepository
) -> Dict[str, str]:
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


def detect_target_repo_comment(comments) -> Dict[str, str]:
    for comment in comments:
        if comment["name"] == TARGET_REPO_KEY:
            return comment


def is_assay_for_target_repo(
    assay_dict: Dict[str, str], target_repo: TargetRepository
) -> Dict[str, str]:
    target_repo_comment = detect_target_repo_comment(assay_dict["comments"])
    if target_repo_comment["value"] == target_repo:
        return True
    else:
        return False


def load_isa_json(file_path: str) -> Union[Dict[str, str], IsaJsonValidationError]:
    with open(file_path, "r") as json_file:
        isa_json = json.load(json_file)

    # TODO: Once we have an idea on what / how to validate, it should be added here

    return isa_json
