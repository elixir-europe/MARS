import pytest
from mars_lib.biosamples_external_references import (
    load_json_file,
    handle_input_dict,
    get_header,
    validate_bs_accession,
)
from json import JSONDecodeError


def test_load_json_file():

    with pytest.raises(
        FileNotFoundError,
        match="The file './tests/fixtures/where_is_my_json.json' does not exist.",
    ):
        load_json_file("./tests/fixtures/where_is_my_json.json")

    with pytest.raises(
        ValueError,
        match="The path './tests/fixtures' is not a file.",
    ):
        load_json_file("./tests/fixtures")

    with pytest.raises(
        ValueError,
        match="The given file './tests/fixtures/not_a_json_file.txt' is not a JSON file based on its extension.",
    ):
        load_json_file("./tests/fixtures/not_a_json_file.txt")

    with pytest.raises(
        ValueError,
        match="The file content of the given file './tests/fixtures/bad_json.json' is not valid JSON.",
    ):
        load_json_file("./tests/fixtures/bad_json.json")


def test_handle_input_dict():
    input = "../test-data/ISA-BH2023-ALL/isa-bh2023-all.json"
    assert handle_input_dict(input)

    not_a_json = "./tests/fixtures/bad_json.json"
    with pytest.raises(
        ValueError,
        match=f"The file content of the given file '{not_a_json}' is not valid JSON.",
    ):
        handle_input_dict(not_a_json)


def test_get_header():
    token = "jkdcbjkbkjsdbjkvb8347yrfiegh3ru3y29yf2g8i39u"
    header = {
        "Content-Type": "application/json;charset=UTF-8",
        "Accept": "application/hal+json",
        "Authorization": "Bearer jkdcbjkbkjsdbjkvb8347yrfiegh3ru3y29yf2g8i39u",
    }
    assert get_header(token) == header


def test_validate_bs_accession():
    invalid_accession = "SAREA112654119"

    with pytest.raises(
        ValueError,
        match=f"The provided accession string '{invalid_accession}' does not match the required format.",
    ):
        validate_bs_accession(invalid_accession)

    valid_accession = "SAMEA112654119"
    assert validate_bs_accession(valid_accession) != ValueError


def test_validate_json_against_schema():
    pass


def test_fetch_bs_json():
    pass


def test_load_bs_json():
    pass


def test_pop_links():
    pass


def test_extend_externalReferences():
    pass
