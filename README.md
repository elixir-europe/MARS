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
- **repository-services**: Code to deploy repository API endpoints that can accept ISAJSON, for testing purposes. See [README](/repository-test-services/README.md) for deployment instructions. 
- **test-data**: Test data to be used in a submission.
- **README.md**: This file

## Stakeholders in MARS
MARS is comprised of distinct stakeholders, each representing key roles in the data submission process. Together, they form the essential building blocks of MARS, enabling cohesive and reliable data submission to multiple repositories.

### End-User
This individual is the user who inputs experimental metadata and location of related data files into ISA-JSON Producer platforms. End-User provides essential experimental details that are encoded into ISA-JSON format.

End-User could be also direct user of the Data Broker platform, particularly in scenarios where the broker platform is only accessible as a web service within the institute's intranet.
* Main function: 
  * Input experimental metadata into ISA-JSON Producer platforms following the specific rules, structures, and requirements of the respective platform. 
  * Use the ISA-JSON producer platform to export experimental metadata in ISA-JSON files and uploading them to the Data Broker platform.
* Responsibilities:
  * End-User ensures the accuracy and completeness of metadata.
  * End-User must refrain from uploading identical ISA-JSON files to the Data Broker platform, unless it is to initiate a distinct action such as update or release.

### Platform that generates ISA-JSON
This role includes any source of ISA-JSON files containing the metadata of multi-omics research. For example, research infrastructure or service facilities that operate platforms such as ARC, FAIRDOM-SEEK, ISA Creators. 

* Main function: Prepare research metadata in ISA-JSON format (i.e. generate and export ISA-JSON files)
* Responsibilities:
  * Ensure compliance of the submitted ISA-JSON with the ISA model expected by MARS.
  * Validate ISA-JSON structure and fields in ISA-JSON as expected by MARS (e.g. “Comment[Target repository]” at Assay level). This includes verification of the checksums associated to the data files. This step might also include validation of repositories’ checklists, controlled vocabulary lists, ontology terms etc, if validation rules are provided by each repository for MARS.
  * Assert that files referenced within the ISA-JSON metadata are uploaded to the target repositories. For example, if a FASTQ file is referenced in an assay that would be submitted to ENA, this file should exist within the storage of ENA. The ISA-JSON metadata payload should contain the checksums associated to the data files (this is requirement for SRA based repositories)
  * Use credentials for submission that the target repositories accept. For example, to submit to ENA, credentials for a submission account of ENA would be required.
 

### Target Archives
This role includes any omics archive that joins the MARS approach, facilitating a submission through the data broker platform. The goal of MARS is for submissions to these repositories to be made in a similar way through metadata in ISA-JSON files.
* Main function: to ingest an ISA-JSON file and use it to submit the metadata it contains to its own archive
* Responsibilities
  * To maintain a service through which the data brokering platform can send the ISA-JSON
  * To create a process that will transform the received ISA-JSON into a suiting format for the submission of their archive. That is, if the archive does not accept natively ISA-JSON
  * To register the metadata targeted to the archive contained in the ISA-JSON
  * Ensure that the response given to the data broker conforms to the given standards. See https://github.com/elixir-europe/biohackathon-projects-2023/blob/main/27/repository-api.md 


## The Data broker

This role includes a research infrastructure or service facilities that operates a platform designed to streamline the data submission process to repositories. This platform serves as the intermediary service in MARS: facilitating seamless submission of (meta)data to the target repositories. Data brokers play a vital role in ensuring the smooth flow of information within the system, connecting data producers with repositories effectively and securely
* Main function: Submit to the target repositories the provided ISA-JSON 
* Responsibilities: 
  * Deliver the ISA-JSON to the repositories without any loss of information.
  * Extend the ISA-JSON with additional information provided by the archives. For example, the accessions assigned to the submitted objects.
  * Handling the submission process as an intermediary between the producers and repositories.
  * Informing the producers effectively about the status of the submission to the target archives.
    * Examples: Reporting errors, or providing the extended ISA-JSON.
  * Secure credential management
  * Data transfer through various protocols (e.g. FTP).
  * To ensure that the brokering account is not used beyond the purposes defined by the producer. In other words, not to modify or submit in the name of the producer without their consent.
The data broker can have an ENA brokering account or not. The data brokering tool or service can be used with a personal account for data repositories.
Data broker reads receipts generated by the repositories and add the information into the ISA-JSON.

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
