# Repository API Specification
This document is to define the interface between the broker and the target repository services.
This applies to all repositories, including BioSamples.

At present, only a single API endpoint is required, `submit`. Authentication and data transfer are not covered in this document, but some assumptions are laid out below.

## Authentication
If the repository requires authentication to submit data, the submit endpoint must allow authentication via an authorization header.

Other specifics remain to be determined.

## Data deposition
If the repository requires data validation in order to accept a submission, then the repository must be able to fetch the file using a URI, which can be included in the ISA-JSON, so that data file validation can be performed at submission time. (For example, an FTP dropbox or Globus.)

At present there is no other requirement for data deposition, this is assumed to be managed by the brokering platform.

## Submit endpoint
`POST /submit`

This endpoint is used to perform a submission to the target repository.

### Request
The submit endpoint must receive ISA-JSON as the body. This is guaranteed to be syntactically correct ISA-JSON and will be filtered to contain only assays to be deposited at the receiving repository and only samples referenced in those assays.

The repository service must accept existing BioSamples accessions in the ISA-JSON and should not broker to BioSamples itself. BioSamples accessions will be present as characteristics of the samples (example [here](https://github.com/elixir-europe/MARS/blob/main/test-data/biosamples-modified-isa.json#L379)).

### Response
The response must be JSON in the following format:
```jsonc
{
    "targetRepository": "repo_id",
    "accessions": [
        // accession objects
    ],
    "errors": [ 
        // error objects
    ],
    "info": [ 
        // info objects
    ]
}
```
where:
* `targetRepository` is the identifier used to annotate the ISA-JSON and should take values from [identifiers.org](http://identifiers.org/)
* Either [`accessions`](#accession-object) OR [`errors`](#error-object), but not both, must be present as a list of objects of the form described below. Presence of this field indicates whether the submission was a success or a failure.
* (optional) [`info`](#info-object) is a list of objects of the form described below. This allows additional repository-specific information to be returned in the response.

#### Accession object
The accession object looks like the following:
```jsonc
{
    "path": [
        {"key": "studies", "where": {"key": "X", "value": "Y"}},
	    {"key": "materials"}
	    // further path objects as needed
    ],
    "value": "REPO_123"
}
```
where:
* `path` describes a JSON query to the object within the ISA-JSON being queried. Each element is an instruction indicating where to navigate next in the JSON, starting from the root.
  * `key` indicates which field to inspect
  * `where` (if necessary) performs a selection if the value of that field is a list
* `value` is the accession assigned to this object

See [examples](#examples) below. The list of accession objects being returned by the repository will be used by the broker to annotate the complete ISA-JSON.

#### Error object
The error object looks like the following:
```jsonc
{
    "type": "error_type",
    "message": "...",
    "path": [
        // path objects as defined above
    ]
}
```
where:
* `message` is a string at the repository’s discretion (though see below)
* `type` is one of the following string values:
  * **INVALID_METADATA**: Indicates a problem with the metadata (ISA-JSON), e.g. missing fields or contains unacceptable values. This can also be used for invalid JSON or invalid ISA, though this will be checked by MARS beforehand.
  * **INVALID_DATA**: Indicates a problem with data, e.g. files weren’t found or weren’t concordant with the metadata.
  * More types to be added as development continues
* (optional) `path` is a JSON query to the pertinent part of the ISA-JSON, defined as described in the accession object section. For example, this might point to an object missing a required field, or an md5 checksum that didn’t match a particular file.

The error objects being returned by the repository may be used by developers to improve data producing or brokering platforms, and as a last resort will be used to provide feedback on problems with the submission to the end user. Repositories should set error messages accordingly, i.e. human-readable, informative, and actionable.

Besides this error reporting, the service should employ other HTTP error codes as usual (e.g. 401).

#### Info object
The info object looks like the following:
```jsonc
{
    "name": "...",
    "message": "..."
}
```
where `name` and `message` are strings at the repository’s discretion.

This can be used to provide any additional information back to the user, not relating to accessions or errors. For example, it could include the submission date and when the data will be made public. This will not be processed further by the broker but will only be presented to the user.

### Examples

#### Submission request
See [here](https://github.com/elixir-europe/MARS/blob/main/test-data/biosamples-input-isa.json).

#### Success response
For illustration only.
```json
{
 "targetRepository": "ena",
 "info": [
   {
     "name": "Submission date",
     "message": "2024-03-22"
   },
   {
     "name": "Release date",
     "message": "2025-03-22"
   }
 ],
 "accessions": [
   {
     "path": [
       {
         "key": "studies",
         "where": {"key": "title", "value": "Arabidopsis thaliana"}
       }
     ],
     "value": "PRJEB100893"
   },
   {
     "path": [
       {
         "key": "studies",
         "where": {"key": "title", "value": "Arabidopsis thaliana"}
       },
       {
         "key": "assays",
         "where": {"key": "@id", "value": "#assay/18_20_21"}
       }
     ],
     "value": "ERR9668871"
   },
   {
     "path": [
       {
         "key": "studies",
         "where": {"key": "title", "value": "Arabidopsis thaliana"}
       },
       {
         "key": "assays",
         "where": {"key": "@id", "value": "#assay/18_20_21"}
       },
       {"key": "materials"},
       {
         "key": "otherMaterials",
         "where": {"key": "@id", "value": "#other_material/332"}
       }
     ],
     "value": "ERX9222846"
   },
   {
     "path": [
       {
         "key": "studies",
         "where": {"key": "title", "value": "Arabidopsis thaliana"}
       },
       {
         "key": "assays",
         "where": {"key": "@id", "value": "#assay/18_20_21"}
       },
       {"key": "materials"},
       {
         "key": "otherMaterials",
         "where": {"key": "@id", "value": "#other_material/333"}
       }
     ],
     "value": "ERX9222847"
   }
 ]
}
```

#### Failure response
```json
{
 "targetRepository": "ena",
 "errors": [
   {
     "type": "INVALID_METADATA",
     "message": "Missing required field collection_date",
     "path": [
       {
         "key": "studies",
         "where": {"key": "title", "value": "Arabidopsis thaliana"}
       }
     ]
   },
   {
     "type": "INVALID_DATA",
     "message": "Could not locate file fake2.bam in the upload location",
     "path": [
       {
         "key": "studies",
         "where": {"key": "title", "value": "Arabidopsis thaliana"}
       },
       {
         "key": "assays",
         "where": {"key": "@id", "value": "#assay/18_20_21"}
       },
       {
         "key": "dataFiles",
         "where": {"key":  "@id", "value":  "#data/334"}
       }
     ]
   }
 ]
}
```
