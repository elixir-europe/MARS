# MARS-CLI

The MARS-CLI tool is a powerful interface for submitting metadata and associated files to various biological repository services like ENA, BioSamples, and MetaboLights. This command-line tool is useful for managing and validating metadata submissions in a ISA-JSON, as well as for automating aspects of repository submissions.

## Installation

This installation procedure describes a typical Linux installation. This application can perfectly work on Windows and MacOS but some of the steps might be different. 

Installing the mars-cli from source:

```sh
cd mars-cli # Assuming you are in the root folder of this project
pip install .
```

If you want to install the optional testing dependencies as well, useful when contributing to the project:

```sh
pip install .[test]
```

If you want to overwrite the `settings.ini` file when reinstalling, you need to set the environmental variable `OVERWRITE_SETTINGS` to `True`:

```sh
OVERWRITE_SETTINGS=True pip install .[test]
```
Installing the MARS-cli, will by default create a `.mars` directory in the home directory to store settings and log files.
If you wish to create the `.mars` directory in another place, you must specify the `MARS_SETTINGS_DIR` variable and set it to the desired path:

```sh
export MARS_SETTINGS_DIR=<path/to/parent_folder/containing/.mars>
```

If you want to make it permanent, you can run to following commands in the terminal.
Note: replace `.bashrc` by the config file of your shell.

```sh
echo '# Add MARS setting directory to PATH' >> $HOME/.bashrc
echo 'export MARS_SETTINGS_DIR=<path/to/parent_folder/containing/.mars>' >> $HOME/.bashrc
```

Once installed, the CLI application will be available from the terminal.

## Configuration

Installing this application will also generate a `settings.ini` file in `$HOME/.mars/`.

```
[logging]
log_level = ERROR
log_file = /my/logging/directory/.mars/app.log
log_max_size = 1024
log_max_files = 5
```

### Repository services

To configure MARS for submissions, modify the configuration file `settings.ini` located at `~/.mars/settings.ini`. Ensure the following content is set:

```ini
[webin]
development-url = https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth
development-token-url = https://wwwdev.ebi.ac.uk/ena/dev/submit/webin/auth/token
production-url = https://www.ebi.ac.uk/ena/submit/webin/auth
production-token-url = https://www.ebi.ac.uk/ena/submit/webin/auth/token

[ena]
development-url = http://localhost:8042/isaena
development-submission-url = http://localhost:8042/isaena/submit
development-data-submission-url = webin2.ebi.ac.uk
production-url = https://www.ebi.ac.uk/ena/submit/webin-v2/
production-submission-url = https://www.ebi.ac.uk/ena/submit/drop-box/submit/?auth=ENA
production-data-submission-url = webin2.ebi.ac.uk

[biosamples]
development-url = http://localhost:8032/isabiosamples
development-submission-url = http://localhost:8032/isabiosamples/submit
production-url = https://www.ebi.ac.uk/biosamples/samples/
production-submission-url = https://www.ebi.ac.uk/biosamples/samples/
```


### Logging

The MARS-CLI will automatically log events to a `.log` file.

__log_level__: The verbosity of logging can be set to three different levels
- CRITICAL: Only critical messages will be logged. __Not recommended!__
- ERROR: Errors and critical messages will be logged.
- WARNING: Warnings, errors and critical messages will be logged.
- INFO: All events are logged.
- DEBUG: For debugging purpose only. __Not recommended as it might log more sensitive information!__
The default setting is ERROR. So only errors are logged!

__log_file__: The path to the log file. By default this will be in `$HOME/.mars/app.log`.

__log_max_size__: The maximum size in kB for the log file. By default the maximum size is set to 1024 kB or 1 MB.

__log_max_files__: The maximum number of old log files to keep. By default, this is set to 5

### Target repository settings

Each of the target repositories have a set of settings:

- development-url: URL to the development server when performing a health-check
- development-submission-url: URL to the development server when performing a submission
- production-url: URL to the production server when performing a health-check
- production-submission-url: URL to the production server when performing a submissionW

## Usage

If you wish to use a different location for the `.mars' folder:

```sh
export MARS_SETTINGS_DIR=<path/to/parent_folder/containing/.mars>
mars-cli [options] <command> ARGUMENT
```

The mars-cli's help text can be found from the command line as such:

```sh
mars-cli --help
```

Output:

```
➜ mars-cli --help
Usage: mars-cli [OPTIONS] COMMAND [ARGS]...

Options:
  -d, --development  Boolean indicating the usage of the development
                     environment of the target repositories. If not present,
                     the production instances will be used.
  --help             Show this message and exit.

Commands:
  health-check       Check the health of the target repositories.
  set-password       Store a password in the keyring.
  submit             Start a submission to the target repositories.
  validate-isa-json  Validate the ISA JSON file.
```

or for a specific command:

```sh
mars-cli submit --help
```

Output:

```
➜ mars-cli submit --help
Usage: mars-cli submit [OPTIONS] ISA_JSON_FILE

  Start a submission to the target repositories.

Options:
  --output TEXT
  --investigation-is-root BOOLEAN
                                  Boolean indicating if the investigation is
                                  the root of the ISA JSON. Set this to True
                                  if the ISA-JSON does not contain a
                                  'investigation' field.
  --submit-to-metabolights BOOLEAN
                                  Submit to Metabolights.
  --data-files FILENAME           Path of files to upload
  --file-transfer TEXT            provide the name of a file transfer
                                  solution, like ftp or aspera
  --submit-to-ena BOOLEAN         Submit to ENA.
  --submit-to-biosamples BOOLEAN  Submit to BioSamples.
  --credentials-file FILENAME     Name of a credentials file
  --username-credentials TEXT     Username from the keyring
  --credential-service-name TEXT  service name from the keyring
  --help                          Show this message and exit.
```

### Development vs production

By default, the mars-CLI will try to submit the ISA-JSON's metadata towards the repositories' production servers.
Passing the development flag will run it in development mode and substitute the production servers with the development
servers.

### Health check repository services

You can check whether the supported repositories are healthy, prior to submission, by doing a health-check.

```sh
mars-cli health-check
```

Output:

```
➜ mars-cli health-check
############# Welcome to the MARS CLI. #############
Running in Production environment
Checking the health of the target repositories.
Service 'webin' healthy and ready to use!
Service 'ena' healthy and ready to use!
Service 'biosamples' healthy and ready to use!
```

### Credential management

This CLI application comes with functionality to interact with your device's keychain backend in order to fetch the necessary credentials.

#### Store a password

You can add a password to keychain:

```sh
mars-cli set-password set-password [OPTIONS] USERNAME

  Store a password in the keyring.

Options:
  --service-name TEXT  You are advised to include service name to match the
                       credentials to. If empty, it defaults to "mars-
                       cli_{DATESTAMP}"
  --password TEXT      The password to store. Note: You are required to
                       confirm the password.
  --help               Show this message and exit.
```


### Options

#### Biosamples submissions 
`--submit-to-biosamples`: By default set to `True`. Will try submit ISA-JSON metadata towards Biosamples. Setting it to `False` will skip sending the ISA-JSON's metadata to Biosamples.

> **Note**: Following command line will avoid submission to Biosamples repository:
```sh
mars-cli submit  --submit-to-biosamples False my-credentials my-isa-json.json
```

#### ENA submissions 
`--submit-to-ena`: By default set to `True`. Will try submit ISA-JSON metadata towards ENA. Setting it to `False` will skip sending the ISA-JSON's metadata to ENA.

> **Note**: Following command line will avoid submission to ENA repository:
```sh
mars-cli submit --submit-to-ena False my-credentials my-isa-json.json
```

`--file-transfer`: Provide the name of a file transfer solution, like ftp or aspera

`--data-file`: Paths of files to upload.

> **Note**: Following command line will submit isa-file and data-file using FTP solution to Biosamples and  ENA:
```sh
mars-cli submit --submit-to-metabolights False --file-transfer ftp --data-files ../data/file_to_upload.fastq.gz my-credentials my-isa-json.json
```

#### Metabolights submissions
> **Status**: 🚧 To Be Developed

`--submit-to-metabolights`: By default set to `True`. Will try to submit ISA-JSON metadata towards Metabolights.
  Setting it to `False` will skip sending the ISA-JSON's metadata to Metabolights.

Following command line will avoid submission to metabolights repository:
```sh
mars-cli submit --submit-to-metabolights False my-credentials my-isa-json.json
```

`--investigation-is-root`: By default this flag is set to false, meaning the ISA-JSON should have the `investigation`
key at the root level. In case the root level __IS__ the investigation (`investigation` level is omitted), you need set
the flag `--investigation-is-root` to `True` in order to validate the ISA-JSON.

```sh
mars-cli submit --investigation-is-root True my-credentials my-isa-json.json
```

`--output`: By default "output_{datetime.now()}", the name of the isa final output.

```sh
mars-cli submit --output final_isa my-credentials my-isa-json.json
```

## Feature: Validation of the ISA JSON
> **Status**: 🚧 To Be Developed

This feature is planned but not yet implemented. Further details will be provided as development progresses.

You can perform a syntactic validation of the ISA-JSON, without submitting to the target repositories.

__Note:__ This does not take repository-side validation into account, nor guarantees successful submission.

### JSONata validation

[JSONata](https://jsonata.org/) is a JSON query and transformation tool that will be used it this project to perform
additional validation
of the ISA-JSON and, in some cases, automatically patch inconsistencies.

This feature is implemented as a set of additional validation rules a user can customize according to the submission
needs.

```sh
mars-cli validate-isa-json --investigation-is-root True ../test-data/biosamples-input-isa.json
```

### Options

`--investigation-is-root`: By default this flag is set to false, meaning the ISA-JSON should have the `investigation`
key at the root level. In case the root level __IS__ the investigation (`investigation` level is omitted), you need set
the flag `--investigation-is-root` to `True` in order to validate the ISA-JSON.

```sh
mars-cli validate-isa-json my-isa-investigation.json
```

## Feature:  Extending BioSamples' records
> **Status**: 🚧 To Be Developed

This part is designed to interact with the BioSamples database, allowing operations like fetching, updating, and extending biosample records.
The script takes in a dictionary of BioSamples' accessions and their associated external references, and expands the former with the latter.


To summarize, the steps of the code are:
1. Takes the BioSamples' submitter credentials and a set of BioSamples accessions and their associated external references
2. Validates inputs
3. For each BioSamples' accession, it downloads its JSON record from BioSamples
4. Extend the BioSamples' JSON with the ``externalReferences`` of the input file
5. Submit the extended JSON to BioSamples to replace the existing one


## Examples

### Submit isa-json to biosamples

After configuring the `settings.ini` file, you can run the MARS CLI tool to submit the isa-json:

```bash
python mars_cli.py --development submit --submit-to-metabolights False --submit-to-ena False --credential-service-name <biosamples> --username-credentials <username> ../test-data/biosamples-input-isa.json
```

- Replace `<biosamples>` with the appropriate service name.
- Replace `<username>` with your BioSamples username.
- Adjust the submission file path (`../test-data/biosamples-input-isa.json`) as needed.

Aternatively, you can also use a credentials file to authenticate to the services. An example can be found here: https://github.com/elixir-europe/MARS/blob/main/mars-cli/tests/test_credentials_example.json
	
Run the MARS CLI tool to submit the isa-json using credentials file:
	
```bash
python mars_cli.py --development submit --submit-to-metabolights False --submit-to-ena False --credentials-file <path_to_your_credentials_file.json> ../test-data/biosamples-input-isa.json
```

### Submit data files and isa-json and to biosamples and ENA


## Deploy repository services

[To set up and run the MARS tool locally using Docker, follow these steps](../repository-services/README.md)


```bash
python mars_cli.py --credential-service-name biosamples  --username-credentials <username> --file-transfer ftp --data-files ../data/ENA_data.R1.fastq.gz --submit-to-metabolights False --output final-isa ../data/biosamples-input-isa.json
```
