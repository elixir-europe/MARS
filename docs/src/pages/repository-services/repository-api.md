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
Data deposition is managed by the brokering platform and should be possible by FTP or Globus. It should take place after initiation but before submission.

## Initiate endpoint
`POST /initiate`

This endpoint is used to initiate a submission to the target repository.

### Request

### Response
The response must be JSON of the following format:
```jsonc
{
  "targetRepository": "repo_id",
  "submissionId": "submission_id",
  "ftpCredentials": {
    "host": "ftp.ebi.ac.uk",
    "upload_path": "/path/to/upload/folder",
    "username": "username",
    "password": "password"
  },
  "comments": [
    // comment objects
  ]
}

```
where:
* (optional) `submissionId` is a temporary internal identifier for the submission. It is only used to track in-flight submissions. If it is missing, it is assumed that the repository will either start a new submission on every run, or has another way of resuming submissions (e.g. via data file identity).
* `ftpCredentials` describes info required to upload data to FTP
* (optional) `comments` is a list of objects described [here](#comment-object).

## Submit endpoint
`POST /submit`

This endpoint is used to perform a submission to the target repository.

### Request
The submit endpoint must receive ISA-JSON as the body. This is guaranteed to be syntactically correct ISA-JSON and will be filtered to contain only assays to be deposited at the receiving repository. If provided by [initiate](#initiate-endpoint), the submission ID will be included as a comment on the assay.

The repository service must accept existing BioSamples accessions in the ISA-JSON and should not broker to BioSamples itself. BioSamples accessions will be present as characteristics of the samples (example [here](https://github.com/elixir-europe/MARS/blob/main/test-data/biosamples-modified-isa.json#L379)).

### Response (MARS Receipt)
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
    "status": {
      // status object
    },
    "info": [ 
        // info objects
    ]
}
```
where:
* `targetRepository` is the identifier used to annotate the ISA-JSON and should take values from [identifiers.org](http://identifiers.org/)
* Exactly one of the following:
  * `accessions`: list of objects defined [here](#accession-object)
  * `errors`: list of objects defined [here](#error-object)
  * `status`: object defined [here](#status-object)
* Presence of `accession`, `errors`, or `status` indicates whether the submission was a success, failure, or is still pending (asynchronous response).
* (optional) `info` is a list of objects of the form described [below](#info-object). This allows additional repository-specific information to be returned in the response.

This object is frequently referred to as the "receipt" or the "MARS receipt".

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

#### Status object
The status object looks like the following:
```jsonc
{
  "statusUrl": "...",
  "submissionId": "..."
}
```
where:
* `statusUrl` is a URL that can be queried to determine the completion status of the submission (see [status endpoint](#submission-status-endpoint) section below)
* `submissionId` is an identifier for the submission

#### Info object
The info object looks like the following:
```jsonc
{
  "name": "...",
  "message": "..."
}
```
where `name` (optional) and `message` are strings at the repository’s discretion.

This can be used to provide any additional information back to the user, not relating to accessions or errors. For example, it could include the submission date and when the data will be made public. This will not be processed further by the broker but will only be presented to the user.

#### Comment object
The comment object allows target repositories to request arbitrary information to be placed into the ISA-JSON as comments. It looks like the following:
```jsonc
{
  "name": "repo_id_...",
  "value": "...",
  "path": [
    // path objects as defined above
  ]
}
```
where:
* `name` is the name of the comment. It must be prefixed by the target repository ID to ensure existing comments are not overwritten.
* `value` is the value for the comment.
* `path` describes where in the ISA-JSON the comment should be placed.

## Submission status endpoint
`GET /{submission_id}/status`

(The endpoint path is only a suggestion, the actual path can differ as long as it is accurately returned in the `status` field of the receipt.)

This endpoint is used to poll for the status of a previous submission. It should be used whenever the time from data and metadata submission until the issuing of accessions exceeds a reasonable duration, and it must be returned in the `status` field of the receipt.

### Response

The response must be the same format as for the submit endpoint (i.e. the [MARS receipt](#response-mars-receipt)), again indicating whether the submission is complete and successful, complete with errors, or still pending.

## Examples

### Submission request
See [here](https://github.com/elixir-europe/MARS/blob/main/test-data/biosamples-input-isa.json).

### Success response
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

### Failure response
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

### Status response
```json
{
  "targetRepository": "eva",
  "status": {
    "id": "123-456",
    "statusUrl": "https://ebi.ac.uk/eva/submission/123-456/status"
  }
}
```