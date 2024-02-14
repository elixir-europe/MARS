import pytest
from mars_lib.biosamples_external_references import (
    BiosamplesRecord,
    load_json_file,
    handle_input_dict,
    get_header,
    validate_bs_accession,
)


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
    bs_record = BiosamplesRecord("SAMEA112654119")

    # Bad end-point
    with pytest.raises(RuntimeError):
        bs_record.fetch_bs_json("https://wwwdev.ebi.ac.uk/biosamples/sample/")

    # Correct end-point
    response = bs_record.fetch_bs_json("https://wwwdev.ebi.ac.uk/biosamples/samples/")
    assert response["accession"] == "SAMEA112654119"


def test_load_bs_json():
    bs_record_from_json = BiosamplesRecord("SAMEA112654119")
    bs_record_from_json.load_bs_json(
        bs_json_file="./tests/fixtures/SAMEA112654119.json"
    )
    assert bs_record_from_json.bs_json["accession"] == "SAMEA112654119"

    bs_dict = load_json_file("./tests/fixtures/SAMEA112654119.json")
    bs_record_from_dict = BiosamplesRecord("SAMEA112654119")
    bs_record_from_dict.load_bs_json(bs_json=bs_dict)
    assert bs_record_from_dict.bs_json["accession"] == "SAMEA112654119"

    bs_record_from_bad_json = BiosamplesRecord("SAMEA112654119")
    with pytest.raises(
        ValueError,
        match="The file content of the given file './tests/fixtures/bad_json.json' is not valid JSON.",
    ):
        bs_record_from_bad_json.load_bs_json(
            bs_json_file="./tests/fixtures/bad_json.json"
        )

    bs_record_from_bad_dict = BiosamplesRecord("SAMEA112654119")
    with pytest.raises(
        TypeError,
        match="Given 'bs_json' is of type '<class 'str'>' instead of type 'dict'.",
    ):
        bs_record_from_bad_dict.load_bs_json(bs_json="This is not even a dict!")


def test_extend_externalReferences():
    bs_record = BiosamplesRecord("SAMEA112654119")
    bs_record.load_bs_json(bs_json_file="./tests/fixtures/SAMEA112654119.json")

    new_ext_refs_list = []
    bs_record.extend_externalReferences(new_ext_refs_list)

    existing_refs = [
        {"url": "https://ega-archive.org/datasets/EGAD00010002458", "duo": []},
        {
            "url": "https://ega-archive.org/metadata/v2/samples/EGAN00004248937",
            "duo": [],
        },
        {"url": "https://www.ebi.ac.uk/ena/browser/view/SAMEA112654119", "duo": []},
    ]

    new_refs = [
        {"url": "https://ega-archive.org/datasets/1", "duo": []},
        {
            "url": "https://ega-archive.org/metadata/v2/samples/2",
            "duo": [],
        },
        {"url": "https://www.ebi.ac.uk/ena/browser/view/3", "duo": []},
    ]

    assert len(existing_refs) == len(bs_record.bs_json["externalReferences"])

    # Order of the external links is different in the BiosamplesRecord from a json,
    # downloaded from https://wwwdev.ebi.ac.uk/biosamples/samples/
    for ext_link in existing_refs:
        assert ext_link in bs_record.bs_json["externalReferences"]

    for ext_link in new_refs:
        assert ext_link not in bs_record.bs_json["externalReferences"]

    bs_record.extend_externalReferences(new_refs)

    for ext_link in existing_refs + new_refs:
        assert ext_link in bs_record.bs_json["externalReferences"]


def test_update_remote_record():
    bs_record = BiosamplesRecord("SAMEA112654119")
    bs_record.fetch_bs_json("https://wwwdev.ebi.ac.uk/biosamples/samples/")

    with pytest.raises(RuntimeError):
        response = bs_record.update_remote_record(get_header("thisIsAFakeToken"))
