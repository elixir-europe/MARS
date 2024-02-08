# MARS: Multi-omics Adapter for Repository Submissions

## Introduction
MARS is a data brokering system for submitting multi-omics life sciences studies to multiple specialized repositories.
MARS is a versatile and modular Multi-Repository ISA Submission Tool that enables data producers and multiple data repositories to exchange information seamlessly using the same standardized ISA-JSON format. Unlike a centralized platform, MARS operates as a common framework, allowing for decentralized data submissions while ensuring consistent interpretation and validation of ISA-JSON containing metadata across various repositories.
MARS serves as a common ground where data producers and diverse data repositories can interact cohesively, sharing scientific metadata encoded in ISA-JSON format. The framework ensures mutual understanding and accurate interpretation of the data, preserving the important links between multi-omics data generated from the same biological source.


## Project structure

```
├── mars-cli
│   ├── lib/
│   ├── mars-cli.py
│   ├── README.md
│   └── ...
├── repository-services
│   ├── isajson-biosamples/
│   │── isajson-ena/
│   ├── docker-compose.yml
│   └── README.md
├── test-data
│   └── ...
└── README.md
```

- **mars-cli**: Source code of the main Python library to submit (meta)data to the repositories. See [README](/mars-cli/README.md) to read more on how to use the command line tool.
- **repository-services**: Code to deploy repository API endpoints that can accept ISAJSON. See [README](/repository-test-services/README.md) for deployment instructions. 
- **test-data**: Test data to be used in a submission.
- **README.md**: This file

## Stakeholders in MARS

MARS is comprised of 4 stakeholders, the end-user, the platform that generates the ISA-JSON, target repositories and the data broker. Each representing key roles in the data submission process. Read more about it in our [stakeholders page](/stakeholders.md).

## Data broker platform

The data

### MARS-CLI

This command line tool is the core of the Data brokering platform and will perform the actual submission of the ISA-JSON and data to the repositories. The application will be build as a Python library which can be used 

1. **Validate the ISA-JSON**: Syntax validation
 => We could use the [ISA-API validation](https://isa-tools.org/isa-api/content/validation.html)

2. **Register samples in BioSamples**: The API call from the data broker triggers the running of the [ISA-JSON to BioSamples code](https://github.com/elixir-europe/biohackathon-projects-2023/tree/main/27/ISABioSamplesProject27): code of the service that consumes the ISA-JSON, parses and submits it to BioSamples. [Example of ISA-JSON that the data broker sends to BioSamples](https://github.com/elixir-europe/biohackathon-projects-2023/blob/main/27/biosamples-input-isa.json).

4. **Update the ISA-JSON with BioSamples information**: After a succesful submission, BioSamples sends to the data broker an updated ISA-JSON containing BioSamples accession numbers for `Source` and `Sample` as `Source characteristics` and `Sample characteristics`, respectively. [Example of ISA-JSON that BioSamples sends back to data broker](https://github.com/elixir-europe/biohackathon-projects-2023/blob/main/27/biosamples-modified-isa.json).

5. Dispatch submissions to other target archives
Data broker uses [this code](?) to make an API call to the other target archives service "ISA-JSON to archive X code".

6. Register linked records in other archives
    The API call from the data broker triggers the running of the
    * [ISA-JSON to ENA code](https://github.com/elixir-europe/biohackathon-projects-2023/tree/main/27/ISASRAProject27): code of the service that consumes the BioSamples accessioned ISA-JSON, parses and submits it to ENA.
    * ISA-JSON to MetaboLights code
    * ISA-JSON to eDAL-PGP code

7. Update the ISA-JSON with other archives' information
    After a succesful submission, each archive sends to the data broker a receipt in a standard format defined for MARS (see example below). The receipt contains the path of the ojects in the ISA-JSON for wich an accession number has been generated, and the related accession number. The structure of the receipt is generic and common for all archives that joins MARS.

    Data broker replaces or updates the original ISA-JSON.

8. Update BioSamples External References
Data broker uses the BioSamples accession numbers to download the submitted BioSamples JSON and extend the `External References` schema by adding the accession numbers provided by the other target archives.

#### Credential management

The application is not responsible for storing and managing credentials, used for submission to the target repositories. Therefor, credentials should be managed by a third party application or platform like Galaxy.

> **To be discussed**:
> Handling brokering accounts: who creates it? Same for all repositories? Who handles requests to broker data? Can it be done automatically? Are all namespaces for submissions would be shared? Check: https://ena-docs.readthedocs.io/en/latest/faq/data_brokering.html

#### Data submission

The application is not to be used as a platform to host data and will not store the data after submission to the target repository. The ISA JSON provided to the application will be updated and stored in the BioSamples repository as an External Reference, but is otherwise considered as ephemeral.
