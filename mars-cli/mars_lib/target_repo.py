from enum import StrEnum


TARGET_REPO_KEY = "target_repository"


class TargetRepository(StrEnum):
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
        return {item for item in cls}
