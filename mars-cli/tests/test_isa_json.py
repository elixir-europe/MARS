import re


from mars_lib.isa_json import (
    reduce_isa_json_for_target_repo,
    load_isa_json,
    update_isa_json,
    map_data_files_to_repositories,
    is_assay_for_target_repo,
)
from mars_lib.target_repo import TargetRepository, TARGET_REPO_KEY
import pytest
from pydantic import ValidationError
from mars_lib.models.isa_json import (
    Data,
    Material,
    Assay,
    Person,
    IsaJson,
    Investigation,
    Study,
)
from mars_lib.models.repository_response import RepositoryResponse
import json


def test_load_isa_json():
    # Should test the validation process of the ISA JSON file where root level = investigation.
    valid_isa_json01 = load_isa_json(
        "../test-data/ISA-BH2023-ALL/isa-bh2023-all.json", True
    )
    assert len(valid_isa_json01.investigation.studies) == 1
    assert valid_isa_json01.investigation.studies[0].identifier == "BH2023"

    # Should test the validation process of the ISA JSON file where root has 'investigation' as key.
    valid_isa_json02 = load_isa_json("../test-data/biosamples-input-isa.json", False)
    assert len(valid_isa_json02.investigation.studies) == 1
    assert valid_isa_json02.investigation.studies[0].title == "Arabidopsis thaliana"

    with pytest.raises(ValidationError):
        load_isa_json("./tests/fixtures/invalid_investigation.json", True)


def test_reduce_isa_json_for_target_repo():
    good_isa_json = load_isa_json(
        "../test-data/ISA-BH2023-ALL/isa-bh2023-all.json", True
    )

    filtered_isa_json = reduce_isa_json_for_target_repo(
        good_isa_json, TargetRepository.ENA.value
    )

    good_isa_json_study = good_isa_json.investigation.studies[0]

    filtered_isa_json_study = filtered_isa_json.investigation.studies[0]

    assert len(good_isa_json_study.assays) == 5
    assert len(filtered_isa_json_study.assays) == 1


def test_reduce_isa_json_for_biosamples():
    good_isa_json = load_isa_json(
        "../test-data/ISA-BH2023-ALL/isa-bh2023-all.json", True
    )

    filtered_isa_json = reduce_isa_json_for_target_repo(
        good_isa_json, TargetRepository.BIOSAMPLES.value
    )

    assert len(filtered_isa_json.investigation.studies[0].assays) == 0


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
                "name": f"{TARGET_REPO_KEY}",
                "value": TargetRepository.ENA.value,
            }
        ],
    }

    invalid_assay_json = {
        "@id": "assay_002",
        "comments": [
            {
                "@id": "comment_002",
                "name": f"{TARGET_REPO_KEY}",
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
                "name": f"{TARGET_REPO_KEY}",
                "value": TargetRepository.ENA.value,
            },
            {
                "@id": "comment_004",
                "name": f"{TARGET_REPO_KEY}",
                "value": TargetRepository.METABOLIGHTS.value,
            },
        ],
    }

    assert Assay.model_validate(valid_assay_json)
    with pytest.raises(
        ValidationError, match=f"Invalid '{TARGET_REPO_KEY}' value: 'my special repo'"
    ):
        Assay.model_validate(invalid_assay_json)

    with pytest.raises(
        ValidationError, match=f"'{TARGET_REPO_KEY}' comment is missing"
    ):
        Assay.model_validate(second_invalid_assay_json)

    with pytest.raises(
        ValidationError, match=f"Multiple '{TARGET_REPO_KEY}' comments found"
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


def test_update_study_materials_no_accession_categories():
    # This file has no characteristics for accessions
    json_path = "../test-data/biosamples-original-isa-no-accesion-char.json"
    with open(json_path) as json_file:
        json_data = json.load(json_file)

    validated_isa_json = IsaJson.model_validate(json_data)

    response_file_path = "tests/fixtures/mars_receipts/biosamples_success_response.json"
    repo_response = RepositoryResponse.from_json_file(response_file_path)

    updated_isa_json = update_isa_json(validated_isa_json, repo_response)

    # Check the accession number of the source
    source_accession = "SAMEA131504583"
    assert (
        updated_isa_json.investigation.studies[0]
        .materials.sources[0]
        .characteristics[-1]
        .value.annotationValue
        == source_accession
    )

    # Check the accession number of the sample
    sample_accession = "SAMEA131504584"
    assert (
        updated_isa_json.investigation.studies[0]
        .materials.samples[0]
        .characteristics[-1]
        .value.annotationValue
        == sample_accession
    )


def test_update_study_materials_with_accession_categories():
    # This file has no characteristics for accessions
    json_path = "../test-data/biosamples-original-isa.json"
    with open(json_path) as json_file:
        json_data = json.load(json_file)

    validated_isa_json = IsaJson.model_validate(json_data)

    response_file_path = "tests/fixtures/mars_receipts/biosamples_success_response.json"
    repo_response = RepositoryResponse.from_json_file(response_file_path)

    updated_isa_json = update_isa_json(validated_isa_json, repo_response)
    # Check the accession number of the source
    source_accession = "SAMEA131504583"
    assert (
        updated_isa_json.investigation.studies[0]
        .materials.sources[0]
        .characteristics[-1]
        .value.annotationValue
        == source_accession
    )

    # Check the accession number of the sample
    sample_accession = "SAMEA131504584"
    assert (
        updated_isa_json.investigation.studies[0]
        .materials.samples[0]
        .characteristics[-1]
        .value.annotationValue
        == sample_accession
    )


def test_update_study_and_assay_with_ena_study_accession_comment():
    json_path = "tests/fixtures/isa_jsons/1_after_biosamples.json"
    isa_json = load_isa_json(json_path, False)
    response_file_path = "tests/fixtures/mars_receipts/ena_success_response.json"
    ena_response = RepositoryResponse.from_json_file(response_file_path)
    ena_study_accession_number = "ERP167466"

    updated_isa_json = update_isa_json(isa_json, ena_response)
    study_comments = updated_isa_json.investigation.studies[0].comments
    accession_comment = filter(
        lambda x: x.name == "ena_study_accession", study_comments
    )
    assert next(accession_comment).value == ena_study_accession_number

    ena_assay = next(
        filter(
            lambda assay: is_assay_for_target_repo(assay, "ena"),
            updated_isa_json.investigation.studies[0].assays,
        ),
        None,
    )
    assay_comments = ena_assay.comments
    accession_comment = filter(
        lambda x: x.name == "ena_study_accession", assay_comments
    )
    assert next(accession_comment).value == ena_study_accession_number


def test_update_datafile_comment_with_accession_comment_present():
    json_path = "tests/fixtures/isa_jsons/1_after_biosamples.json"
    isa_json = load_isa_json(json_path, False)
    response_file_path = "tests/fixtures/mars_receipts/ena_success_response.json"
    ena_response = RepositoryResponse.from_json_file(response_file_path)
    data_file_accession_number = "ERR00000001"

    updated_isa_json = update_isa_json(isa_json, ena_response)
    data_file_comments = (
        updated_isa_json.investigation.studies[0].assays[0].dataFiles[0].comments
    )
    accession_comment = filter(lambda x: x.name == "accession", data_file_comments)
    assert next(accession_comment).value == data_file_accession_number


def test_filename_validation():
    # ISA should have a filename that starts with 'x_'
    with pytest.raises(ValidationError, match="'filename' should start with 'i_'"):
        Investigation.model_validate({"@id": "1", "filename": "bad filename"})

    with pytest.raises(ValidationError, match="'filename' should start with 's_'"):
        Study.model_validate({"@id": "2", "filename": "bad filename"})

    with pytest.raises(ValidationError, match="'filename' should start with 'a_'"):
        Assay.model_validate({"@id": "3", "filename": "bad filename"})

    assert re.match(r"^i_", "i_Good_file_name")

    assert Investigation.model_validate({"@id": "4", "filename": "i_Good_File_Name"})
    assert Study.model_validate({"@id": "5", "filename": "s_Good_File_Name"})
    assert Assay.model_validate({"@id": "6", "filename": "a_Good_File_Name"})


def test_map_data_files_to_repositories():
    isa_json = load_isa_json(
        file_path="../test-data/ISA-BH2024-ALL/isa-bh2024-all.json",
        investigation_is_root=True,
    )
    exact_match_files = [
        "../test-data/ISA-BH2024-ALL/cnv-seq-data-0.fastq",
        "../test-data/ISA-BH2024-ALL/cnv-seq-data-1.fastq",
        "../test-data/ISA-BH2024-ALL/cnv-seq-data-2.fastq",
        "../test-data/ISA-BH2024-ALL/cnv-seq-data-3.fastq",
        "../test-data/ISA-BH2024-ALL/metpro-analysis.txt",
        "../test-data/ISA-BH2024-ALL/ms-data-metpro--1.mzml",
        "../test-data/ISA-BH2024-ALL/ms-data-metpro--2.mzml",
        "../test-data/ISA-BH2024-ALL/ms-data-metpro--3.mzml",
        "../test-data/ISA-BH2024-ALL/ms-data-metpro--4.mzml",
        "../test-data/ISA-BH2024-ALL/rna-seq-data-0.fastq",
        "../test-data/ISA-BH2024-ALL/rna-seq-data-1.fastq",
        "../test-data/ISA-BH2024-ALL/rna-seq-data-2.fastq",
        "../test-data/ISA-BH2024-ALL/rna-seq-data-3.fastq",
    ]

    check_map = dict(
        {
            "metabolights": [
                "../test-data/ISA-BH2024-ALL/metpro-analysis.txt",
                "../test-data/ISA-BH2024-ALL/ms-data-metpro--1.mzml",
                "../test-data/ISA-BH2024-ALL/ms-data-metpro--2.mzml",
                "../test-data/ISA-BH2024-ALL/ms-data-metpro--3.mzml",
                "../test-data/ISA-BH2024-ALL/ms-data-metpro--4.mzml",
            ],
            "arrayexpress": [
                "../test-data/ISA-BH2024-ALL/rna-seq-data-0.fastq",
                "../test-data/ISA-BH2024-ALL/rna-seq-data-1.fastq",
                "../test-data/ISA-BH2024-ALL/rna-seq-data-2.fastq",
                "../test-data/ISA-BH2024-ALL/rna-seq-data-3.fastq",
            ],
            "eva": [
                "../test-data/ISA-BH2024-ALL/cnv-seq-data-0.fastq",
                "../test-data/ISA-BH2024-ALL/cnv-seq-data-1.fastq",
                "../test-data/ISA-BH2024-ALL/cnv-seq-data-2.fastq",
                "../test-data/ISA-BH2024-ALL/cnv-seq-data-3.fastq",
            ],
        }
    )
    assert check_map == map_data_files_to_repositories(exact_match_files, isa_json)

    not_enough_files = exact_match_files[:-1]

    with pytest.raises(
        ValueError,
        match=rf"Assay for repository '{TargetRepository.ARRAYEXPRESS.value}' has encountered",
    ):
        map_data_files_to_repositories(not_enough_files, isa_json)

    too_many_files = exact_match_files.copy()
    one_too_many = "../test-data/ISA-BH2024-ALL/one-too-many.fastq"
    too_many_files.append(one_too_many)

    result_maps = map_data_files_to_repositories(too_many_files, isa_json)

    assert one_too_many not in [
        value for key, value_list in result_maps.items() for value in value_list
    ]

    duplicated_files = exact_match_files.copy()
    duplicated_files.append(exact_match_files[-1])

    map_data_files_to_repositories(duplicated_files, isa_json)
