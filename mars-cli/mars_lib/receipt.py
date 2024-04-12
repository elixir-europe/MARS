from enum import Enum
import json


class ReceiptRepository(str, Enum):
    """
    List of repository identifiers from https://identifiers.org/
    """
    ENA = "ena.embl"
    """
    https://registry.identifiers.org/registry/ena.embl
    """
    METABOLIGHTS = "metabolights"
    """
    https://registry.identifiers.org/registry/metabolights
    """


class ReceiptField(str, Enum):
    TARGET_REPOSITORY = "targetRepository"
    ACCESSIONS = "accessions"
    ERRORS = "errors"
    INFO = "info"
    KEY = "key"
    VALUE = "value"
    PATH = "path"
    WHERE = "where"


class ReceiptEnumEncoder(json.JSONEncoder):
    """
    Json encoder for fields that are Enums
    """
    def default(self, obj):
        if isinstance(obj, Enum):
            return obj.value
        return json.JSONEncoder.default(self, obj)

