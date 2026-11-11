---
layout: ../../layouts/Layout.astro
---

# Repository API Specification
This document is to define the interface between the broker and the target repository services.
This applies to all repositories, including BioSamples.

There are two required endpoints, `initiate` and `submit`. Authentication and data transfer are not covered in this document, but some assumptions are laid out below.

## Authentication
If the repository requires authentication to submit data, the below endpoints must allow authentication via an authorization header.

## Data deposition
Data deposition is done by FTP or Globus and is managed by the brokering platform. It should take place after initiation but before metadata submission.

## Initiate endpoint
`POST /initiate`

This endpoint is used to initiate a submission to the target repository. It is also used to fetch data upload information for a pending submission: the repository adapter must recognise when a submission ID corresponds to an existing submission.

### Request
The initiate endpoint must accept ISA-JSON as the body. This is guaranteed to match what is sent to [submit](#submit-endpoint) (i.e. syntactically valid and filtered), including potentially an existing submission id.

### Response
The response must be JSON of the following format:
```jsonc
{
  "submissionId": "submission_id",
  "ftpCredentials": {
    "host": "ftp.ebi.ac.uk",
    "uploadPath": "/path/to/upload/folder",
    "username": "username",
    "password": "password"
  },
  // other file transfer credentials to be added as needed
  "assayComments": [
    // assay comment objects
  ]
}

```
where:
* (optional) `submissionId` is a temporary internal identifier for the submission. It is only used to track in-flight submissions. If it is missing, it is assumed that the repository will either start a new submission on every run, or has another way of resuming submissions (e.g. via data file identity).
* `ftpCredentials` describes info required to upload data to FTP.
* (optional) `assayComments` is a list of objects described [here](#assay-comment-object). This allows additional repository-specific information to be added to the ISA-JSON at the assay level.

## Submit endpoint
`POST /submit`

This endpoint is used to perform a submission to the target repository. It is also used to poll for status for a pending submission: the repository adapter must recognise when a submission ID corresponds to an existing submission.

### Request
The submit endpoint must receive ISA-JSON as the body. This is guaranteed to be syntactically correct ISA-JSON and will be filtered to contain only assays to be deposited at the receiving repository.

If provided by [initiate](#initiate-endpoint), the submission ID will be included as a comment on the assay with the name `<repo_id>_submission_id` (e.g. `eva_submission_id`).

The repository service must accept existing BioSamples accessions in the ISA-JSON and should not broker to BioSamples itself. BioSamples accessions will be present as characteristics of the samples (example [here](https://github.com/elixir-europe/MARS/blob/main/test-data/biosamples-modified-isa.json#L379)).

### Response (MARS Receipt)
The response must be JSON in the following format, often referred to as the "MARS receipt".
```jsonc
{
    "targetRepository": "repo_id",
    "submissionStatus": "status",
    "accessions": [
        // accession objects
    ],
    "errors": [ 
        // error objects
    ],
    "assayComments": [
        // comment objects
    ],
    "info": [ 
        // info objects
    ]
}
```
where:
* `targetRepository` is the identifier used to annotate the ISA-JSON and should take values from [identifiers.org](http://identifiers.org/)
* `submissionStatus`: indicates the status of the submission and is one of the following string values:
  * **SUCCESS**: the submission has completed successfully, accessions are returned.
  * **FAILURE**: the submission has failed, errors are returned.
  * **PENDING**: the submission is still being processed, client should query `submit` again to get the final status.
* `accessions`: list of objects defined [here](#accession-object). Must be present only if status is `SUCCESS`.
* `errors`: list of objects defined [here](#error-object). Must be present only if status is `FAILURE`.
* (optional) `assayComments` is a list of objects described [here](#assay-comment-object). This allows additional repository-specific information to be added to the ISA-JSON at the assay level.
* (optional) `info` is a list of objects of the form described [below](#info-object). This allows additional repository-specific information to be returned in the response but not persisted.

The service should employ appropriate HTTP status codes to indicate success or failure of the request; `submissionStatus` is only to indicate the status of the submission as a whole. For example, if the service can respond to the request but the submission as a whole is invalid, the response code should be `200` while the `submissionStatus` should be **FAILURE**.

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

#### Assay comment object
The comment object allows target repositories to request arbitrary information (e.g. release date) to be placed into the ISA-JSON as comments at the assay level. It looks like the following:
```jsonc
{
  "name": "repo_id_...",
  "value": "..."
}
```
where:
* `name` is the name of the comment. It must be prefixed by the target repository ID to ensure existing comments are not overwritten.
* `value` is the value for the comment, any string value chosen by the repository.

#### Info object
The info object can be used to provide any additional information back to the user, not relating to errors and not persisted to the ISA-JSON. It looks like the following:
```jsonc
{
  "name": "...",
  "message": "..."
}
```
where `name` (optional) and `message` are strings at the repository’s discretion.

## Examples

### Submission request
See [here](https://github.com/elixir-europe/MARS/blob/main/test-data/biosamples-input-isa.json).

### Success response
For illustration only.
```json
{
 "targetRepository": "ena",
 "submissionStatus": "SUCCESS",
 "assayComments": [
   {
     "name": "ena_submission_date",
     "value": "2024-03-22"
   },
   {
     "name": "ena_release_date",
     "value": "2025-03-22"
   }
 ],
 "accessions": [
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

### Failure response
```json
{
 "targetRepository": "ena",
 "submissionStatus": "FAILURE",
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
