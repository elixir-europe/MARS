# MARS: Multi-omics Adapter for Repository Submissions

**MARS** is a framework for submitting multi-omics life-science studies to multiple target repositories using the **ISA-JSON** metadata standard.

Full documentation and concepts are hosted on the MARS website: https://elixir-europe.github.io/MARS/


## Key components

![Overview](/docs/src/assets/MARS_overview.svg)


### ISA-JSON in MARS

- How MARS uses ISA-JSON for multi-omics metadata exchange: https://elixir-europe.github.io/MARS/#isa-json-as-metadata-carrier
- Human readable presentation of the MARS ISA-JSON schema: https://elixir-europe.github.io/MARS/mars-isa-json

### Stakeholders

- End-users, ISA-JSON–producing platforms, data brokers, and repositories: https://elixir-europe.github.io/MARS/stakeholders

### Repository API (Adapters)

- Standard for programmatic ISA-JSON ingestion and receipt generation: https://elixir-europe.github.io/MARS/repository-services/repository-api
- Java utilities—including the standard receipt model—for developers building MARS-compatible repository adapters: https://github.com/elixir-europe/mars-repository-lib

### MARS-cli

- Python command-line tool and library for multi omics ISA-JSON submissionsubmissions to multiple repositories: https://github.com/elixir-europe/mars-cli


## Repository Structure


```text
├── docs/
│   └── ...
├── repository-services
│   ├── isajson-biosamples/
│   ├── isajson-ena/
│   └── README.md
├── schemas
│   └── ...
├── test-data
│   └── ...
└── README.md
```

- **docs**: Astro project powering the public MARS documentation site.
- **repository-services**: Code to deploy repository API endpoints that can accept ISAJSON. See [README](/repository-services/README.md) for deployment instructions. 
- **schemas**: JSON schema representation of the pydantic models used for syntactic validation of the ISA-JSON
- **test-data**: Test data to be used in a submission.
- **README.md**: This file.

