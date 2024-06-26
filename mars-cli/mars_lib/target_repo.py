from enum import Enum


TARGET_REPO_KEY = "target repository"


class TargetRepository(str, Enum):
    """
    Holds constants, tied to the target repositories.
    """

    ENA = "ena"
    METABOLIGHTS = "metabolights"
    BIOSAMPLES = "biosamples"
    EVA = "eva"
