# MARS: Multi-omics Adapter for Repository Submissions

## Introduction

MARS is a data brokering initiative for submitting multi-omics life sciences studies to multiple specialized repositories.
It is setup to be modular and enables data producers and multiple data repositories to exchange information seamlessly using the same standardized ISA-JSON format. Unlike a centralized platform, MARS operates as a common framework, allowing for decentralized data submissions while ensuring consistent interpretation and validation of ISA-JSON containing metadata across various repositories.
The initiative ensures mutual understanding and accurate interpretation of the data, preserving the important links between multi-omics data generated from the same biological source.

## Stakeholders in MARS

MARS is comprised of multiple stakeholders, the end-user, the platform that generates the ISA-JSON, target repositories and the data broker. Each representing key roles in the data submission process. Read more about it in our [stakeholders page](/stakeholders.md).

## ISA-JSON as metadata carrier

We use [ISA-JSON](https://isatools.readthedocs.io/en/latest/isamodel.html) to store and interchange metadata between the end-user and the target repositories because:

- **Standardization**: ISA-JSON follows the ISA structure (Investigation- Study - Assay), ensuring structured metadata descriptions.
- **Versatile**: It is not bound by any domain and can represent multi-omics experimental metadata.
- **Interoperability**: Since ISA-JSON follows a standard format, it facilitates interoperability between different software tools and platforms that support the ISA standard. 
- **Community Adoption**: Widely adopted within the life sciences research community for metadata standardization.


## Data broker platform

A platform provided by the [Data broker](/stakeholders.md#data-broker) that should:

  * Accept an ISA-JSON as input and submit it to the repositories without any loss of information.
  * Extend the ISA-JSON with additional information provided by the target repositories. For example, the accessions assigned to the submitted objects.
  * Process reporting errors
  * Enable secure credential management and the possibility to set brokering accounts.
  * (Could) Support data transfer through various protocols (e.g. FTP). This would include the verification of the checksums associated to the data files. 
  * Setting up a brokering account or not.
  * To ensure that the brokering account is not used beyond the purposes defined by the producer. In other words, not to modify or submit in the name of the producer without their consent.


### MARS-CLI

This command line tool (CLI) is the core of the Data broker platform and will perform the actual submission of the ISA-JSON and data to the repositories. The application will be build as a Python library which can be integrated in other platforms including ARC and Galaxy. Source code and documentation can be found in the [mars_cli folder](/) in this repo.

The main steps are:

1. **Validate the ISA-JSON**: Syntax validation
 => We could use the [ISA-API validation](https://isa-tools.org/isa-api/content/validation.html)

2. **Register samples in BioSamples**: The API call from the data broker triggers the running of the [ISA-JSON to BioSamples code](https://github.com/elixir-europe/biohackathon-projects-2023/tree/main/27/ISABioSamplesProject27): code of the service that consumes the ISA-JSON, parses and submits it to BioSamples. [Example of ISA-JSON that the data broker sends to BioSamples](https://github.com/elixir-europe/biohackathon-projects-2023/blob/main/27/biosamples-input-isa.json).

4. **Update the ISA-JSON with BioSamples information**: After a succesful submission, BioSamples sends to the data broker an updated ISA-JSON containing BioSamples accession numbers for `Source` and `Sample` as `Source characteristics` and `Sample characteristics`, respectively. [Example of ISA-JSON that BioSamples sends back to data broker](https://github.com/elixir-europe/biohackathon-projects-2023/blob/main/27/biosamples-modified-isa.json).

5. **Dispatch submissions to other target archives**: Data broker uses [this code](?) to make an API call to the other target archives service "ISA-JSON to archive X code".

6. **Register linked records in other archives**:
    The API call from the data broker triggers the running of the
    * [ISA-JSON to ENA code](https://github.com/elixir-europe/biohackathon-projects-2023/tree/main/27/ISASRAProject27): code of the service that consumes the BioSamples accessioned ISA-JSON, parses and submits it to ENA.
    * ISA-JSON to MetaboLights code
    * ISA-JSON to eDAL-PGP code

7. **Update the ISA-JSON with other archives' information**: After a succesful submission, each archive sends to the data broker a receipt in a standard format defined for MARS (see example below). The receipt contains the path of the ojects in the ISA-JSON for wich an accession number has been generated, and the related accession number. The structure of the receipt is generic and common for all archives that joins MARS.

Data broker replaces or updates the original ISA-JSON.

8. **Update BioSamples External References**: Data broker uses the BioSamples accession numbers to download the submitted BioSamples JSON and extend the `External References` schema by adding the accession numbers provided by the other target archives.

#### Credential management

The application is not responsible for storing and managing credentials, used for submission to the target repositories. Therefor, credentials should be managed by a third party application or platform like Galaxy.

> **To be discussed**:
> Handling brokering accounts: who creates it? Same for all repositories? Who handles requests to broker data? Can it be done automatically? Are all namespaces for submissions would be shared? Check: https://ena-docs.readthedocs.io/en/latest/faq/data_brokering.html

#### Data submission

The application is not to be used as a platform to host data and will not store the data after submission to the target repository. The ISA JSON provided to the application will be updated and stored in the BioSamples repository as an External Reference, but is otherwise considered as ephemeral.

## ISA-JSON support by repositories

THE MARS initiative 

## File structure

```
├── mars-cli
│   ├── mars_lib/
│   ├── mars_cli.py
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
- **repository-services**: Code to deploy repository API endpoints that can accept ISAJSON. See [README](/repository-services/README.md) for deployment instructions. 
- **test-data**: Test data to be used in a submission.
- **README.md**: This file
