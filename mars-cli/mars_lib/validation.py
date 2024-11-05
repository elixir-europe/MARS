from mars_lib.models.isa_json import IsaJson
from typing import Union, Any


class CustomValidationException(Exception):
    # Remove class if unnecessary
    pass


def validate(
    isa_json: IsaJson, validation_schema: dict[str, Any]
) -> Union[IsaJson, CustomValidationException]:
    if validation_schema is None:
        raise CustomValidationException("Validation schema not provided")
    # TODO: write validation / patching logic
    return isa_json
