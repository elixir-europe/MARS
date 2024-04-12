import json
from typing import Any, Dict
from receipt import ReceiptEnumEncoder, ReceiptField, ReceiptRepository


def to_json(response: Dict[ReceiptField, Any]) -> Any:
    """
    Converts the response dictionary to json
    """
    return json.loads(json.dumps(response, cls=ReceiptEnumEncoder))


def interpret(receipt: Dict, isa_json: Dict) -> Dict[ReceiptField, Any]:
    """
    Prepares the response

    Parameters
    ----------
    receipt : Dict
        The Json receipt from ENA
    isa_json: Dict
        The requested ISA Json

    Returns
    -------
    Dict
        More details about the response format can be found here:
        https://github.com/elixir-europe/MARS/blob/refactor/repository-services/repository-api.md#response
    """

    response = {
        ReceiptField.TARGET_REPOSITORY: ReceiptRepository.ENA,
        ReceiptField.ACCESSIONS: [],
        ReceiptField.ERRORS: receipt["messages"].get("error", []),
        ReceiptField.INFO: receipt["messages"].get("info", []),
    }

    if not successfull(ena_receipt):
        return response

    for study in isa_json["investigation"]["studies"]:
        isa_json_study_title = study["title"]
        ena_study = next((item for item in receipt["projects"] if item["alias"] == isa_json_study_title), None)

        if ena_study is None:
            response[ReceiptField.ERRORS].append(f"Cannot find a study with the alias '{isa_json_study_title}' in the ENA receipt")
            continue

        response[ReceiptField.ACCESSIONS].append(get_study_response(isa_json_study_title, ena_study))
        for sample in study["materials"]["samples"]:
            isa_json_sample_id = sample["@id"]
            ena_sample = next((item for item in receipt["samples"] if item["alias"] == isa_json_sample_id), None)

            if ena_sample is None:
                response[ReceiptField.ERRORS].append(f"Cannot find a sample with the alias '{isa_json_sample_id}' in the ENA receipt")
                continue

            response[ReceiptField.ACCESSIONS].append(get_sample_response(isa_json_study_title, isa_json_sample_id, ena_sample))

    return response


def successfull(receipt: Dict) -> bool:
    """
    Checks the result of the ENA receipt

    Parameters
    ----------
    receipt : Dict
        The Json receipt from ENA

    Returns
    -------
    bool
        Is successfull or not
    """

    return receipt["success"]


def get_study_response(title: str, receipt_study: Dict) -> Dict:
    return {
        ReceiptField.PATH: [
            {
                ReceiptField.KEY: "investigation"
            },
            {
                ReceiptField.KEY: "studies",
                ReceiptField.WHERE: {
                    ReceiptField.KEY: "title",
                    ReceiptField.VALUE: title,
                },
            },
        ],
        ReceiptField.VALUE.value: receipt_study["accession"],
    }


def get_sample_response(study_title: str, sample_id: str, sample: Dict) -> Dict:
    return {
        ReceiptField.PATH: [
            {
                ReceiptField.KEY: "investigation"
            },
            {
                ReceiptField.KEY: "studies",
                ReceiptField.WHERE: {
                    ReceiptField.KEY: "title",
                    ReceiptField.VALUE: study_title,
                },
            },
            {
                ReceiptField.KEY: "materials"
            },
            {
                ReceiptField.KEY: "samples",
                ReceiptField.WHERE: {
                    ReceiptField.KEY: "@id",
                    ReceiptField.VALUE: sample_id
                }
            }
        ],
        ReceiptField.VALUE: sample["accession"],
    }


def get_experiment_response():
    # TODO: Preparing the response for ISA-JSON assay from ENA experiment
    raise NotImplementedError(f"{get_experiment_response.__name__}() is not implemented!")


def get_run_response():
    # TODO: Preparing the response for ISA-JSON dataFile from ENA run
    raise NotImplementedError(f"{get_run_response.__name__}() is not implemented!")



if __name__ == "__main__":

    isa_json_file_path = "./test-data/biosamples-input-isa.json"
    ena_receipt_file_path = "./test-data/ena-receipt.json"
    
    with open(ena_receipt_file_path,"r") as ena_receipt_file:
        with open(isa_json_file_path, "r") as isa_json_file:
            ena_receipt = json.load(ena_receipt_file)
            isa_json = json.load(isa_json_file)
            response = interpret(ena_receipt, isa_json)

            print(to_json(response))
