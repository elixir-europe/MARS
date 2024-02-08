# MARS: Multi-omics Adapter for Repository Submissions

## Introduction

MARS is a data brokering initiative for submitting multi-omics life sciences studies to multiple specialized repositories.
It is setup to be modular and enables data producers and multiple data repositories to exchange information seamlessly using the same standardized ISA-JSON format. Unlike a centralized platform, MARS operates as a common framework, allowing for decentralized data submissions while ensuring consistent interpretation and validation of ISA-JSON containing metadata across various repositories.
The initiative ensures mutual understanding and accurate interpretation of the data, preserving the important links between multi-omics data generated from the same biological source.

## Stakeholders

MARS is comprised of multiple stakeholders, the end-user, the platform that generates the ISA-JSON, target repositories and the data broker. Each representing key roles in the data submission process. Read more about it in our [stakeholders page](/stakeholders.md).


## Components


![MARS overview](/MARS_overview.svg)


### ISA-JSON as metadata carrier

We use [ISA-JSON](https://isatools.readthedocs.io/en/latest/isamodel.html) to store and interchange metadata between the end-user and the target repositories because:

- **Standardization**: ISA-JSON follows the ISA structure (Investigation- Study - Assay), ensuring structured metadata descriptions.
- **Versatile**: It is not bound to any domain and can represent multi-omics experimental metadata.
- **Interoperability**: Since ISA-JSON follows a standard format, it facilitates interoperability between different software tools and platforms that support the ISA standard. 
- **Community Adoption**: Widely adopted within the life sciences research community for metadata standardization.

It is produced by the [ISA-JSON producing platforms](/stakeholders.md#isa-json-producing-platforms) and will be the metadata input of the Data broker platform, see below.

### Data broker platform

A platform operated by the [Data broker](/stakeholders.md#data-broker) should:

* Accept an ISA-JSON as input and submit it to the repositories without any loss of information.
* Extend the ISA-JSON with additional information provided by the target repositories. For example, the accessions assigned to the submitted objects.
* Process reporting errors
* Enable secure credential management and the possibility to set brokering accounts.
* Supports data transfer through various protocols (e.g. FTP). This would include the verification of the checksums associated to the data files. 
* Allows the Data broker to set up a brokering account or the end-user a personal account.
* To ensure that the brokering account is not used beyond the purposes defined by the producer. In other words, not to modify or submit in the name of the producer without their consent.

> **To be discussed**:
> Handling brokering accounts: who creates it? Same for all repositories? Who handles requests to broker data? Can it be done automatically? Are all namespaces for submissions would be shared? Check: https://ena-docs.readthedocs.io/en/latest/faq/data_brokering.html


Examples of Data broker platforms are ARC, Galaxy, ...

### MARS-CLI

This command line tool (CLI) will be used by the Data broker platform and will perform the actual submission of the ISA-JSON to the repositories. The application will be build as a Python library which can be integrated in a web application, ARC, Galaxy and others. Source code and documentation can be found in the [mars_cli folder](/) in this repo.

The main steps of MARS-CLI are:

1. **Validating the ISA-JSON**: Syntax validation

    => We could use the [ISA-API validation](https://isa-tools.org/isa-api/content/validation.html) library.

2. **Registering samples in BioSamples**: Submitting an ISA-JSON to a newly developed API at BioSamples. The BioSamples accession will be reused by the other repositories and thus needs to be done first.
After a successful submission, BioSamples sends back an updated ISA-JSON containing BioSamples accession numbers for `Source` and `Sample` as `Source characteristics` and `Sample characteristics`, respectively. 

    => The source code for the ISA-JSON API for BioSamples can be found in the [repository-services repo](/repository-services/isajson-biosamples/) and can be used for testing

3. **Filtering the ISA-JSON**: The ISA-JSON (updated with BioSamples IDs) has to be filtered for every target repository so it only contains information relevant for that repo

    => We can use the `targetRepository` attribute to select assays.

4. **Registering linked records to other repositories**: Sending the filtered ISA-JSON to the endpoints of the repositories who accept ISA-JSON. 

    => Currently we have ISA-JSON API services ready for ENA and BioSamples. This could be done asynchronously.

5. **Processing the receipts and errors from other repositories**: After a successful submission, each repository sends back a receipt in a standard format defined for MARS (see [repository-api info](/repository-services/repository-api.md)). The receipt contains the path of the objects in the ISA-JSON for which an accession number has been generated, and the related accession number. 

    => We have to handle well when submissions go wrong.

6. **Update the ISA-JSON with repositories' information**: Based on the information in the receipts, the ISA-JSON can be populated with accession numbers linked to the submitted metadata objects.

7. **Update BioSamples External References**: Data broker uses the BioSamples accession numbers to download the submitted BioSamples JSON and extend the `External References` schema by adding the accession numbers provided by the other target archives.

    => *Done* by Marco, see [biosamples_externalReferences.py](mars-cli/mars_lib/biosamples_external_references.py)


#### Credential management

MARS-CLI is not responsible for storing and managing credentials, used for submission to the target repositories. Therefor, credentials should be managed by the [Data broker platform](#data-broker-platform).


#### Data submission

MARS-CLI is not to be used as a platform to host data and will not store the data after submission to the target repository. This should be handled by the [Data broker platform](#data-broker-platform). The ISA-JSON provided to the application will be updated and stored in the BioSamples repository as an External Reference, but is otherwise considered as ephemeral.

=> Data submission could be added to MARS-CLI?

### ISA-JSON support by repositories

ISA-JSON API services are being developed and deployed by the repositories that are part of the MARS initiative. This includes programmatic submission, the digestion of ISA-JSON in order to register the metadata objects and the creation of a receipt according to the MARS [repository-api](/repository-services/repository-api.md) standard.
 Track the status of each repository here:

| Repository | Programmatic submission | Development status | Deployed? | Source code |
|---|---|---|---|---|
| [BioSamples](https://www.ebi.ac.uk/biosamples/) | yes | Ready to be tested | no | [GitHub](repository-services/isajson-biosamples) |
| [ENA](https://www.ebi.ac.uk/ena/browser/) | yes | Ready to be tested | no | [GitHub](repository-services/isajson-json) |
| [MetaboLights](https://www.ebi.ac.uk/metabolights/) | NA | Not started | no |  |
| [BioStudies/ArrayExpress](https://www.ebi.ac.uk/biostudies/arrayexpress) | yes, in dev | Not started | no |  |
| [e!DAL-PGP](https://edal-pgp.ipk-gatersleben.de/) | NA | Not started | no |  |
| Your repository here? Join MARS!  |

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
