from enum import Enum


TARGET_REPO_KEY = "target_repository"


class TargetRepository(str, Enum):
    """
    Holds constants, tied to the target repositories.
    """

    ENA = "ena"
    METABOLIGHTS = "metabolights"
    BIOSAMPLES = "biosamples"
    EVA = "eva"
    ARRAYEXPRESS = "arrayexpress"

    @classmethod
    def available_repositories(cls):
        return {item.value for item in cls}
