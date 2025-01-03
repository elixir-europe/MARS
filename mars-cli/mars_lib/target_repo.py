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

    @classmethod
    def get_repository_urls_from_config(
        cls, config
    ) -> dict[str, dict[str, dict[str, str]]]:
        return {
            "DEV": {
                "ENA": {
                    "SERVICE": config.get(
                        "ena",
                        "development-url",
                        fallback="https://wwwdev.ebi.ac.uk/biosamples/samples",
                    ),
                    "SUBMISSION": config.get(
                        "ena",
                        "development-submission-url",
                        fallback="https://wwwdev.ebi.ac.uk/biosamples/samples/submit",
                    ),
                    "DATA-SUBMISSION": config.get(
                        "ena",
                        "development-data-submission-url",
                        fallback="webin2.ebi.ac.uk",
                    ),
                },
                "WEBIN": {
                    "SERVICE": config.get(
                        "webin",
                        "development-url",
                        fallback="https://wwwdev.ebi.ac.uk/ena/submit/webin/auth",
                    ),
                    "TOKEN": config.get(
                        "webin",
                        "development-token-url",
                        fallback="https://wwwdev.ebi.ac.uk/ena/submit/webin/auth/token",
                    ),
                },
                "METABOLIGHTS": {
                    "SERVICE": config.get(
                        "metabolights",
                        "development-url",
                        fallback="https://www-test.ebi.ac.uk/metabolights/mars/ws3/submissions/",
                    ),
                    "SUBMISSION": config.get(
                        "metabolights",
                        "development-submission-url",
                        fallback="https://www-test.ebi.ac.uk/metabolights/mars/ws3/submissions/",
                    ),
                    "TOKEN": config.get(
                        "metabolights",
                        "development-token-url",
                        fallback="https://www-test.ebi.ac.uk/metabolights/mars/ws3/auth/token",
                    ),
                },
                "BIOSAMPLES": {
                    "SERVICE": config.get(
                        "biosamples",
                        "development-url",
                        fallback="https://wwwdev.ebi.ac.uk/biosamples/samples/",
                    ),
                    "SUBMISSION": config.get(
                        "biosamples",
                        "development-submission-url",
                        fallback="https://wwwdev.ebi.ac.uk/biosamples/samples/",
                    ),
                },
            },
            "PROD": {
                "ENA": {
                    "SERVICE": config.get(
                        "ena",
                        "production-url",
                        fallback="https://www.ebi.ac.uk/ena/submit/webin-v2/",
                    ),
                    "SUBMISSION": config.get(
                        "ena",
                        "production-submission-url",
                        fallback="https://www.ebi.ac.uk/ena/submit/drop-box/submit/?auth=ENA",
                    ),
                    "DATA-SUBMISSION": config.get(
                        "ena",
                        "development-data-submission-url",
                        fallback="webin2.ebi.ac.uk",
                    ),
                },
                "WEBIN": {
                    "SERVICE": config.get(
                        "webin",
                        "production-url",
                        fallback="https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth",
                    ),
                    "TOKEN": config.get(
                        "webin",
                        "production-token-url",
                        fallback="https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth/token",
                    ),
                },
                "METABOLIGHTS": {
                    "SERVICE": config.get(
                        "metabolights",
                        "production-url",
                        fallback="https://www-test.ebi.ac.uk/metabolights/mars/ws3/submissions/",
                    ),
                    "SUBMISSION": config.get(
                        "metabolights",
                        "production-submission-url",
                        fallback="https://www-test.ebi.ac.uk/metabolights/mars/ws3/submissions/",
                    ),
                    "TOKEN": config.get(
                        "metabolights",
                        "production-token-url",
                        fallback="https://www-test.ebi.ac.uk/metabolights/mars/ws3/auth/token",
                    ),
                },
                "BIOSAMPLES": {
                    "SERVICE": config.get(
                        "biosamples",
                        "production-url",
                        fallback="https://www.ebi.ac.uk/biosamples/samples/",
                    ),
                    "SUBMISSION": config.get(
                        "biosamples",
                        "production-submission-url",
                        fallback="https://www.ebi.ac.uk/biosamples/samples/",
                    ),
                },
            },
        }
