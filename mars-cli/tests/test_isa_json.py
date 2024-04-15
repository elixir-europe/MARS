from mars_lib.isa_json import (
    reduce_isa_json_for_target_repo,
    load_isa_json,
    TargetRepository,
)
import pytest
from pydantic import ValidationError


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
