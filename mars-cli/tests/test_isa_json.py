from mars_lib.isa_json import (
    reduce_isa_json_for_target_repo,
    load_isa_json,
)
from mars_lib.target_repo import TargetRepository, TARGET_REPO_KEY
import pytest
from pydantic import ValidationError
from mars_lib.model import Data, Material, Assay, Person


def test_load_isa_json():
    # Should test the validation process
    valid_isa_json = load_isa_json("../test-data/ISA-BH2023-ALL/isa-bh2023-all.json")
    assert len(valid_isa_json.studies) == 1
    assert valid_isa_json.studies[0].identifier == "BH2023"

    with pytest.raises(ValidationError):
        load_isa_json("./tests/fixtures/invalid_investigation.json")


def test_reduce_isa_json_for_target_repo():
    good_isa_json = load_isa_json("../test-data/ISA-BH2023-ALL/isa-bh2023-all.json")

    filtered_isa_json = reduce_isa_json_for_target_repo(
        good_isa_json, TargetRepository.ENA
    )

    good_isa_json_study = good_isa_json.studies[0]

    filtered_isa_json_study = filtered_isa_json.studies[0]

    assert len(good_isa_json_study.assays) == 5
    assert len(filtered_isa_json_study.assays) == 1


def test_data_type_validator():
    valid_data_json = {"@id": "data_001", "name": "data 1", "type": "Image File"}

    invalid_data_json = {
        "@id": "data_001",
        "name": "data 1",
        "type": "Custom File",  # This is not a valid data type
    }

    assert Data.model_validate(valid_data_json)

    with pytest.raises(ValidationError):
        Data.model_validate(invalid_data_json)


def test_material_type_validator():
    valid_material_json = {
        "@id": "material_001",
        "name": "material 1",
        "type": "Extract Name",
    }

    invalid_material_json = {
        "@id": "material_002",
        "name": "material 2",
        "type": "Custom Material",  # This is not a valid material type
    }

    assert Material.model_validate(valid_material_json)

    with pytest.raises(ValidationError):
        Material.model_validate(invalid_material_json)


def test_target_repo_comment_validator():
    valid_assay_json = {
        "@id": "assay_001",
        "comments": [
            {
                "@id": "comment_001",
                "name": "target repository",
                "value": TargetRepository.ENA,
            }
        ],
    }

    invalid_assay_json = {
        "@id": "assay_002",
        "comments": [
            {
                "@id": "comment_002",
                "name": "target repository",
                "value": "my special repo",
            }
        ],
    }

    second_invalid_assay_json = {"@id": "assay_003", "comments": []}

    third_invalid_assay_json = {
        "@id": "assay_004",
        "comments": [
            {
                "@id": "comment_003",
                "name": "target repository",
                "value": TargetRepository.ENA,
            },
            {
                "@id": "comment_004",
                "name": "target repository",
                "value": TargetRepository.METABOLIGHTS,
            },
        ],
    }

    assert Assay.model_validate(valid_assay_json)
    with pytest.raises(
        ValidationError, match="Invalid 'target repository' value: 'my special repo'"
    ):
        Assay.model_validate(invalid_assay_json)

    with pytest.raises(ValidationError, match="'target repository' comment is missing"):
        Assay.model_validate(second_invalid_assay_json)

    with pytest.raises(
        ValidationError, match="Multiple 'target repository' comments found"
    ):
        Assay.model_validate(third_invalid_assay_json)

    def test_person_phone_nr_validator():
        valid_person_json = {
            "@id": "person_001",
            "phone_nr": "+49123456789",
        }

        invalid_person_json = {
            "@id": "person_002",
            "phone_nr": "123456789",
        }

        assert Person.model_validate(valid_person_json)

        with pytest.raises(ValidationError, match="Invalid number format"):
            Person.model_validate(invalid_person_json)
