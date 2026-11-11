---
layout: ../layouts/Layout.astro
---

# Placing repository attributes in MARS ISA-JSON
*Which MARS ISA-JSON level a repository attribute belongs to, based on what it describes.*

When mapping a repository's metadata field into MARS ISA-JSON, the level it belongs
to — Investigation, Study, or Assay — is determined by what the attribute describes,
not by which repository defines it or how it's presented in that repository's own
schema. This page states that rule and how to apply it.

## Investigation level

Place an attribute at Investigation level if it describes or applies to the **overall
investigation**, independent of any one study within it.

Typical examples:
- Publications associated with the investigation as a whole
- People / contacts for the investigation
- Investigation title, description

## Study level

Place an attribute at Study level if it describes **one particular study**,
independent of modality.

Typical examples:
- Study title
- Study description or abstract
- Study-level publications
- People
- Study sources and samples used for one or more modality-specific assays: organism, geographic location

## Assay level

Place an attribute at Assay level if it is specific to the **technology, modality,
or sample** used for that measurement.

Typical examples:
- Target repository name
- Target repository specific "study accession number"
- Any other sample characteristic or technology/platform/modality-specific attribute, such as library preparation details

## Decision rule

| What the attribute concerns or refers to | MARS ISA-JSON level |
|---|---|
| The investigation as a whole (e.g. publications, people) | Investigation |
| One study as a whole (e.g. title, description, abstract, people, publication, study source and sample information applicatble to one or more assays) | Study |
| A technology or modality specific information (e.g. target repository study accession, library preparation per sample) | Assay |

Apply this rule to every repository attribute before looking for or creating a field
mapping. A repository attribute that is schematically attached to, say, a "project"
object in a repository does not automatically belong at Investigation
level in MARS ISA-JSON — its scope, not its home in the source schema, decides its
level.

## Where to go next

- For the complete, generated MARS ISA-JSON field reference:
  <https://elixir-europe.github.io/MARS/mars-isa-json/>
- For how specific repository attributes resolve to specific MARS ISA-JSON fields,
  see the per-repository mapping pages: 
  - BioSamples - <https://elixir-europe.github.io/MARS/biosamples-example.md/>
  - ENA
  - MetaboLights
  Each page applies the rule on this page attribute by attribute, for that
  repository's full schema.