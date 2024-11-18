---
title: 'MARS: Multi-omics Adapter for Repository Submissions, preparing for launch'
title_short: 'BioHackEU24 #23: MARS'
tags:
  - ISA-JSON
  - data submission
  - multi-omics studies
  - biohackeu24
authors:
  - name: April Shen
    orcid: 0000-0000-0000-0000
    affiliation: 1
  - name: Bert Droesbeke
    orcid: 0000-0000-0000-0000
    affiliation: 2
  - name: Dipayan Gupta
    orcid: 0000-0000-0000-0000
    affiliation: 3
  - name: Flora D'Anna
    orcid: 0000-0000-0000-0000
    affiliation: 4
  - name: Kevin De Pelseneer
    orcid: 0000-0000-0000-0000
    affiliation: 5
  - name: Laurent Bouri
    orcid: 0000-0000-0000-0000
    affiliation: 6
  - name: Ozgur Yurekten
    orcid: 0000-0000-0000-0000
    affiliation: 7
  - name: Pedram A. Keyvani
    orcid: 0000-0000-0000-0000
    affiliation: 8
  - name: Philippe Rocca-Serra
    orcid: 0000-0000-0000-0000
    affiliation: 9
  - name: Stella Eggels
    orcid: 0000-0000-0000-0000
    affiliation: 10
  - name: Xiaoran Zhou
    orcid: 0000-0000-0000-0000
    affiliation: 11
affiliations:
  - name: First Affiliation
    index: 1
  - name: Second Affiliation
    index: 2
  - name: Second Affiliation
    index: 3
  - name: Second Affiliation
    index: 4
  - name: Second Affiliation
    index: 5
  - name: Second Affiliation
    index: 6
  - name: Second Affiliation
    index: 7
  - name: Second Affiliation
    index: 8
date: 8 November 2024
cito-bibliography: paper.bib
event: BH24EU
biohackathon_name: "BioHackathon Europe 2024"
biohackathon_url:   "https://biohackathon-europe.org/"
biohackathon_location: "Barcelona, Spain, 2024"
group: Project 23
# URL to project git repo --- should contain the actual paper.md:
git_url: https://github.com/biohackrxiv/publication-template
# This is the short authors description that is used at the
# bottom of the generated paper (typically the first two authors):
authors_short: April Shen, Bert Droesbeke \emph{et al.}
---


# Introduction

As part of the BioHackathon Europe 2024, we here report the progress made on the project 'MARS: Multi-omics Adapter for Repository Submissions, preparing for launch'. This projects builds on the work done during BioHackathon Europe 2022 and 2023, and continued as part of the ELIXIR Data Platform project (2024-2026).

Multimodality studies are a reality, with scientists commonly using several different data acquisition techniques to characterise biological systems under various experimental conditions. Yet, the deposition of such studies to public repositories remains a challenge for scientists who need familiarity with individual repositories to achieve these data publication requirements.

During this Biohackathon project we produced a proof of concept for the implementation of the MARS initiative. The proof of concept dispatches metadata and data to BioSamples, ENA and MetaboLights using the ISA-JSON format.

# Results

This document use Markdown and you can look at [this tutorial](https://www.markdowntutorial.com/).

Please keep sections to a maximum of only two levels.

## BioSamples' adaptor for MARS ISA-JSON

As part of the MARS initiative, BioSamples created an adaptor to retrieve Study Sources, Study Samples, and their associated metadata attributes and values by reading the MARS ISA-JSON. The relationship between Sources and Samples is maintained through the parent-child hierarchy in BioSamples.

In Investigation -> Studies -> Materials -> Sources, take 'id'. 

For each Source 'id' -> take 'id', name (e.g plant 1), -> characteristicsCategory -> take category 'id' (i.e name of metadata field) and value -> annotationValue = Parent BioSamples entry

In Investigation -> Studies -> Materials -> Samples -> take 'id' (i.e id of the sample), name (i.e. leaf 1) & characteristicsCategory = Child BioSamples entry

In Investigation -> Studies -> Materials -> Samples -> derivesFrom -> look for the same source 'id' as above.

Add BioSamples Parent-Child relationship between 'Sample id' the 'derived from' 'Source id'.

## BioSamples' receipt for MARS and ISA-JSON update

A BioSamples submission through MARS-CLI triggers a response in the form of a receipt, formatted according to the MARS specifications. This receipt includes the accession numbers of the BioSamples entries, along with the precise ISA-JSON paths pointing to the related Sources and Samples within the MARS ISA-JSON. This setup enables the ISA-JSON to be updated with the accession numbers provided by BioSamples, using MARS-CLI.

## ENA's adaptor for MARS ISA-JSON

As part of the MARS initiative, ENA developed an adaptor to retrieve nucleic acid sequencing data along with their associated metadata and values by reading the MARS ISA-JSON.

The ENA adaptor for MARS identifies the relevant Assay in the ISA-JSON using the `target_repository` attribute, which is added as a comment to the Assay in the ISA-JSON.

Data files to be submitted to ENA are identified by the adaptor by using the `dataFiles` section in the Assay of interest. 

The adaptor also uses material types labeled as "Library Name" to locate metadata attributes and values needed to populate the EXPERIMENT XML (SRA). Additional metadata attributes relevant to ENA may be stored as protocol parameter values within the ISA-JSON. Therefore, the adaptor utilizes the `processSequences` section to identify the protocol and its parameter values associated with the Library material.

Pseudocode:  
```
Assay → dataFiles “type = Raw Data File”, “name=xxx” and “id=xxx”
```

JSONata:  
```
$.studies.assays.dataFiles[type=“Raw Data File*”]
```
## ENA's receipt for MARS and ISA-JSON update
xxxx

# Discussion and future work

## BioSamples' adaptor for MARS ISA-JSON

Additional features must be added to the BioSamples' adaptor for MARS in order to be able to capture the complete set of metadata attributes which are stored in ISA-JSON as protocol parameter values.

Investigation -> Studies -> processSequence

Look for “outputs = 'id' of the Samples”.

For each output id = Sample id, list the parameterValues and annotate the Samples.

## Receipt for MARS and ISA-JSON update

Must be reviewed to formalise placing of accession numbers for study and how to store this information in ISA-JSON. Also the repository identifier must match what sent by ISA-JSON at the start.

## ENA's adaptor for MARS ISA-JSON

### Review the capability to capture all data files comments.

Pseudocode:  
assay.dataFiles.comments -> take all comments, both names and values (i.e. name= file type; value = fastq)

### A functionality to check and/or generate file checksums (including the method to do so if they are missing) must be defined and implemented in the code.

JSONata:  
$.studies.assays.dataFiles[type=“Raw Data File*”].comments[name=”checksum”]

### Review the parameter values and link between libraries and data files relations. Edge cases.

Pseudocode:  
Assay → processSequence → look for “outputs = 'id' of the data file”
For each “outputs = 'id' of the data file”, read input 'id'

For each input id → Material  =  characteristicsCategory=id= Library Name

If Material =  characteristicsCategory=id= Library Name
Go back to input id → Link outputs = 'id' of the data file & Library Name=input id
stop

If Material ≠   characteristicsCategory=id= Library Name is Not found
Go back to input id → look for “outputs = input 'id' ”

Repeat loop

### Potential reviewed logic to iterate through caracteristicsCategory and parameterValues related to Library material.

For each Library Name=input id listed during the previous step
Go to Material → fetch characteristicsCategory id & value
Associate it to Library Name

For each Library Name=input id listed during the previous step
Go to processSequence → take outputs = Library Name
For each Library Name output, → parameterValues categoryID (name) & value
Associate it to Library Name outputs ids

For each Library Name=input id listed during the previous step
Go to processSequence → take inputs = Library Name (or go to previousProcess)
For each Library Name input, → parameterValues categoryID (name) & value
Associate it to Library Name ids

### Identify relation between LIBRARY Name (experiment alias) and Sample ID

Starting from Library Name=input/output id listed during the previous step

Assay -> processSequence → look for “outputs = Library Name id”
For each “outputs = Library Name id”, read input id
For each input id → Material Samples = Library Name

If Material Samples = Sample Name
Go back to output  id → Link outputs = Library Name & Sample Name= input id
stop

If Material Samples ≠  Sample Name
Go back to input id → look for “outputs = input 'id' ”
Repeat loop



## Metadata
what to do with additional attributes not expected by repositories.


# Discussion

...

## Acknowledgements
use elixir syntax for aknowledgement
...

## References



## Tables and figures

Tables can be added in the following way, though alternatives are possible:

Table: Note that table caption is automatically numbered and should be
given before the table itself.

| Header 1 | Header 2 |
| -------- | -------- |
| item 1 | item 2 |
| item 3 | item 4 |

A figure is added with:

![Caption for BioHackrXiv logo figure](./biohackrxiv.png)

# Other main section on your manuscript level 1

Lists can be added with:

1. Item 1
2. Item 2

# Citation Typing Ontology annotation

You can use [CiTO](http://purl.org/spar/cito/2018-02-12) annotations, as explained in [this BioHackathon Europe 2021 write up](https://raw.githubusercontent.com/biohackrxiv/bhxiv-metadata/main/doc/elixir_biohackathon2021/paper.md) and [this CiTO Pilot](https://www.biomedcentral.com/collections/cito).
Using this template, you can cite an article and indicate _why_ you cite that article, for instance DisGeNET-RDF [@citesAsAuthority:Queralt2016].

The syntax in Markdown is as follows: a single intention annotation looks like
`[@usesMethodIn:Krewinkel2017]`; two or more intentions are separated
with colons, like `[@extends:discusses:Nielsen2017Scholia]`. When you cite two
different articles, you use this syntax: `[@citesAsDataSource:Ammar2022ETL; @citesAsDataSource:Arend2022BioHackEU22]`.

Possible CiTO typing annotation include:

* citesAsDataSource: when you point the reader to a source of data which may explain a claim
* usesDataFrom: when you reuse somehow (and elaborate on) the data in the cited entity
* usesMethodIn
* citesAsAuthority
* citesAsEvidence
* citesAsPotentialSolution
* citesAsRecommendedReading
* citesAsRelated
* citesAsSourceDocument
* citesForInformation
* confirms
* documents
* providesDataFor
* obtainsSupportFrom
* discusses
* extends
* agreesWith
* disagreesWith
* updates
* citation: generic citation

