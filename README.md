# MARS: Multi-omics Adapter for Repository Submissions

## Introduction

MARS is a data brokering initiative designed to facilitate the submission of multi-omics life sciences studies to multiple specialized repositories. Built as a modular system, MARS enables seamless data exchange between producers and repositories using the standardized ISA-JSON format.

Unlike centralized platforms, MARS functions as a common framework for decentralized data submissions while ensuring consistent interpretation and validation of ISA-JSON metadata across various repositories. This approach preserves important links between multi-omics datasets derived from the same biological source, ensuring mutual understanding and accurate data interpretation.

## Stakeholders

MARS is comprised of multiple stakeholders: the end-user, the platform that generates the ISA-JSON, target repositories and the data broker. Each represents key roles in the data submission process. Read more about it in our [stakeholders page](/stakeholders.md).


## Components

![MARS overview](/MARS_overview.svg)


### ISA-JSON as metadata carrier

We use [ISA-JSON](https://isatools.readthedocs.io/en/latest/isamodel.html) to store and interchange metadata between the end-user and the target repositories because:

- **Standardization**: ISA-JSON follows the ISA structure (Investigation- Study - Assay), ensuring structured metadata descriptions.
- **Versatile**: It is not bound to any domain and can represent multi-omics experimental metadata.
- **Interoperability**: Since ISA-JSON follows a standard format, it facilitates interoperability between different software tools and platforms that support the ISA standard. 
- **Community Adoption**: Widely adopted within the life sciences research community for metadata standardization.

ISA-JSON is generated by [ISA-JSON producing platforms](/stakeholders.md#isa-json-producing-platforms) and serves as the metadata input for the Data Broker platform, as outlined below.s makes it more fluid while maintaining clarity and precision. Let me know if you'd like any further refinements!

### Data broker platform

A platform operated by the [Data broker](/stakeholders.md#data-broker) should:  

- Accept ISA-JSON as input and submit it to target repositories without any loss of information.  
- Extend the ISA-JSON with additional information from the repositories, such as accession numbers assigned to submitted objects.  
- Handle error reporting efficiently.  
- Maintain an active submission process throughout its duration (up to multiple days), including waiting for repository-side validation steps to complete.  
- Enable secure credential management.  
- Support data transfer via various protocols (e.g., FTP), ensuring checksum verification for data integrity.  
- Allow the Data broker to set up a brokering account or enable end-users to create personal accounts.  

Examples of Data broker platforms include ARC, Galaxy, and others.  


### MARS-CLI

MARS-CLI is a command-line tool (CLI) used by the Data Broker platform to handle the submission of ISA-JSON metadata to multiple repositories. It automates the submission process, updates ISA-JSON with accession numbers based on repository responses, and ensures smooth data integration.  

Built as a Python library, MARS-CLI can be integrated into web applications, ARC, Galaxy, and other platforms. The source code and documentation are available in the [mars-cli folder](/mars-cli/) in this repository.  

#### Main Steps of MARS-CLI  

1. **Ingesting and Validating the ISA-JSON**  
MARS-CLI requires certain mandatory fields beyond the standard ISA specification (e.g., `target_repository` as a comment). Upon ingestion, the ISA-JSON is loaded into memory and validated using Pydantic to ensure it meets these requirements.  

2. **Identifying the Target Repositories**  
The order of submission depends on the repositories specified in the ISA-JSON. MARS-CLI determines the correct sequence for submitting metadata and data.  

3. **Registering Samples in BioSamples**  
MARS-CLI first submits the ISA-JSON to BioSamples via a newly developed API. BioSamples accessions are crucial since other repositories reuse them.  

- After submission, BioSamples returns a receipt containing accessions for `Source` and `Sample`, mapped to `Source characteristics` and `Sample characteristics`, respectively.  
- Source Code: The BioSamples ISA-JSON API can be found in the [repository-services repo](/repository-services/isajson-biosamples/) and is available for testing.  

4. **Filtering the ISA-JSON**  
Once updated with BioSamples accessions, the ISA-JSON is filtered for each target repository. This ensures that only relevant metadata is submitted to each repository. The filtering is based on the `target repository` attribute present in the ISA-JSON assays.  

5. **Submitting Data to Target Repositories**  
MARS-CLI uploads data using FTP. Some repositories require that data files are present in their upload space before metadata submission. This step ensures that data availability and checksum validation are completed before sending metadata.  

6. **Registering ISA-JSON at the Target Repositories**  
The filtered ISA-JSON is submitted to repositories that accept ISA-JSON metadata, such as ENA. The MARS project helps out with the adaptation of ISA-JSON by the repositories using the so called adapters. 

- Source Code: The ISA-JSON API for ENA is available in the [repository-services repo](/repository-services/isajson-ena/) and can be used for testing.  

7. **Processing Repository Receipts and Errors**  
After submission, each repository returns a receipt in a standardized format defined for MARS (see [repository-api info](/repository-services/repository-api.md)).  

- The receipt includes the paths of objects within the ISA-JSON and their assigned accession numbers.  
- Errors encountered during submission are processed and reported accordingly.  

8. **Updating BioSamples External References**  
The Data Broker retrieves the BioSamples JSONs of the submitted samples using its accession numbers and updates the `External References` schema by adding the accession numbers assigned by other target repositories.  

9. **Generating an Updated ISA-JSON with Repository Information**  
Based on the information in repository receipts, the ISA-JSON is updated with accession numbers linked to submitted metadata objects. This final, enriched ISA-JSON can then be provided as output.  

#### Credential management

MARS-CLI is not responsible for storing and managing credentials, used for submission to the target repositories. Therefor, credentials should be managed by the [Data broker platform](#data-broker-platform).


#### Data submission

MARS-CLI is not to be used as a platform to host data and will not store the data after submission to the target repository. This should be handled by the [Data broker platform](#data-broker-platform). The ISA-JSON provided to the application will be updated and stored in the BioSamples repository as an External Reference, but is otherwise considered as ephemeral.


### ISA-JSON support by repositories

ISA-JSON API services are being developed and deployed by the repositories that are part of the MARS initiative. This includes programmatic submission, the ingestion of ISA-JSON in order to register the metadata objects and the creation of a receipt according to the MARS [repository-api](/repository-services/repository-api.md) standard.

Track the status of each repository here:

| Repository | Programmatic submission | Development status | Deployed | Source code |
|---|---|---|---|---|
| [BioSamples](https://www.ebi.ac.uk/biosamples/) | yes | PoC being improved | no | [GitHub](repository-services/isajson-biosamples) |
| [ENA](https://www.ebi.ac.uk/ena/browser/) | yes | PoC being improved | no | [GitHub](repository-services/isajson-ena) |
| [MetaboLights](https://www.ebi.ac.uk/metabolights/) | yes | Proof of concept | no |  |
| [BioStudies/ArrayExpress](https://www.ebi.ac.uk/biostudies/arrayexpress) | yes, in dev | Not started | no |  |
| [e!DAL-PGP](https://edal-pgp.ipk-gatersleben.de/) | NA | Not started | no |  |
| Your repository here? Join MARS!  |  |  |  |  | 

## File structure in this repo

```
├── mars-cli
│   ├── mars_lib/
│   ├── mars_cli.py
│   ├── README.md
│   └── ...
├── repository-services
│   ├── isajson-biosamples/
│   │── isajson-ena/
│   ├── repository-api.md
│   └── README.md
│   └── ...
├── test-data
│   └── ...
└── README.md
```

- **mars-cli**: Source code of the Python library to submit (meta)data to the repositories. See [README](/mars-cli/README.md) to read more on how to use the command line tool.
- **repository-services**: Code to deploy repository API endpoints that can accept ISAJSON. See [README](/repository-services/README.md) for deployment instructions. 
    - **repository-api.md**: Describing the receipt standard for repository APIs to follow.
- **test-data**: Test data to be used in a submission.
- **README.md**: This file

## Acknowledgements

This project was initiated during the ELIXIR Europe BioHackathon 2022 and has since received continued support through subsequent ELIXIR Hackathons and the ELIXIR Data Platform WP2.
