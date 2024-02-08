# Stakeholders in MARS

MARS framework is comprised of distinct stakeholders, each representing key roles in the data submission process. Together, they form the essential building blocks of MARS framework, enabling cohesive and reliable data submission to multiple repositories.
Each stakeholder has a specific role, performs specific functions and carries distinct responsibilities essential for the smooth operation of the system.

## End-user
This individual is the user who inputs experimental metadata and location of related data files into ISA-JSON producing platforms. The end-user provides essential experimental details that are encoded into ISA-JSON format.

The end-user could also be the direct user of the [Data broker platform](/README.md#data-broker-platform), particularly in scenarios where the broker platform is only accessible as a web service within the institute's intranet.
* Main function: 
  * Input experimental metadata into ISA-JSON Producer platforms following the specific rules, structures, and requirements of the respective platform. 
  * Use the ISA-JSON producer platform to export experimental metadata in ISA-JSON files and uploading them to the Data Broker platform.
* Responsibilities:
  * End-User ensures the accuracy and completeness of metadata.
  * End-User must refrain from uploading identical ISA-JSON files to the Data Broker platform, unless it is to initiate a distinct action such as update or release.

## ISA-JSON producing platforms
This role includes any source of ISA-JSON files containing the metadata of multi-omics research. For example, research infrastructure or service facilities that operate platforms such as ARC, FAIRDOM-SEEK, ISA Creators. 

* Main function: Prepare research metadata in ISA-JSON format (i.e. generate and export ISA-JSON files)
* Responsibilities:
  * Ensure compliance of the submitted ISA-JSON with the ISA model expected by MARS.
  * Validate ISA-JSON structure and fields in ISA-JSON as expected by MARS (e.g. “Comment[Target repository]” at Assay level). This includes verification of the checksums associated to the data files. This step might also include validation of repositories’ checklists, controlled vocabulary lists, ontology terms etc, if validation rules are provided by each repository for MARS.
  * Assert that files referenced within the ISA-JSON metadata are uploaded to the target repositories. For example, if a FASTQ file is referenced in an assay that would be submitted to ENA, this file should exist within the storage of ENA. The ISA-JSON metadata payload should contain the checksums associated to the data files (this is requirement for SRA based repositories)
  * Use credentials for submission that the target repositories accept. For example, to submit to ENA, credentials for a submission account of ENA would be required.
 
## Data broker
This role includes a research infrastructure or service facilities that operates a [Data broker platform](/README.md#data-broker-platform) designed to streamline the data submission process to repositories. This platform serves as the intermediary service in MARS: facilitating seamless submission of (meta)data to the target repositories. Data brokers play a vital role in ensuring the smooth flow of information within the system, connecting data producers with repositories effectively and securely
* Main function: Submit to the target repositories the provided ISA-JSON 
* Responsibilities: 
  * Deliver the ISA-JSON to the repositories without any loss of information.
  * Extend the ISA-JSON with additional information provided by the archives. For example, the accessions assigned to the submitted objects.
  * Handling the submission process as an intermediary between the producers and repositories.
  * Informing the producers effectively about the status of the submission to the target archives.
    * Examples: Reporting errors, or providing the extended ISA-JSON.
  * Secure credential management
  * Data transfer through various protocols (e.g. FTP).
  * Setting up a brokering account or not.
  * To ensure that the brokering account is not used beyond the purposes defined by the producer. In other words, not to modify or submit in the name of the producer without their consent.


## Target repositories
This role includes any omics archive that joins the MARS approach, facilitating a submission through the data broker platform. The goal of MARS is for submissions to these repositories to be made in a similar way through metadata in ISA-JSON files.
* Main function: to ingest an ISA-JSON file and use it to submit the metadata it contains to its own archive
* Responsibilities
  * To maintain a service through which the data brokering platform can send the ISA-JSON
  * To create a process that will transform the received ISA-JSON into a suiting format for the submission of their archive. That is, if the archive does not accept natively ISA-JSON
  * To register the metadata targeted to the archive contained in the ISA-JSON
  * Ensure that the response given to the data broker conforms to the given standards. See https://github.com/elixir-europe/biohackathon-projects-2023/blob/main/27/repository-api.md
