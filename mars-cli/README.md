# Installing the mars-cli

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

# Configuration

Installing this application will also generate a `settings.ini` file in `$HOME/.mars/`.

```
[logging]
log_level = ERROR
log_file = /my/logging/directory/.mars/app.log
log_max_size = 1024
log_max_files = 5
```

## Logging

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

## Target repository settings

Each of the target repositories have a set of settings:

- development-url: URL to the development server when performing a health-check
- development-submission-url: URL to the development server when performing a submission
- production-url: URL to the production server when performing a health-check
- production-submission-url: URL to the production server when performing a submissionW

# Using the MARS-CLI

If you wish to use a different location for the `.mars' folder:

```sh
export MARS_SETTINGS_DIR=<path/to/parent_folder/containing/.mars>
mars-cli [options] <command> ARGUMENT
```

## Help

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
############# Welcome to the MARS CLI. #############
Running in Production environment
Usage: mars-cli submit [OPTIONS] CREDENTIALS_FILE ISA_JSON_FILE

  Start a submission to the target repositories.

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

## Development

By default, the mars-CLI will try to submit the ISA-JSON's metadata towards the repositories' production servers.
Passing the development flag will run it in development mode and substitute the production servers with the development
servers.

## Health check repository services

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
Checking production instances.
Webin (https://www.ebi.ac.uk/ena/submit/webin/auth) is healthy.
ENA (https://www.ebi.ac.uk/ena/submit/webin-v2/) is healthy.
Biosamples (https://www.ebi.ac.uk/biosamples/samples/) is healthy.
```

## using the keychain

This CLI application comes with functionality to interact with your device's keychain backend.

### Store a password

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

## Submitting to repository services

TODO

### Options

- `--submit-to-ena`: By default set to `True`. Will try submit ISA-JSON metadata towards ENA. Setting it to `False` will skip sending the ISA-JSON's metadata to ENA.

```sh
mars-cli submit --submit-to-ena False my-credentials my-isa-json.json
```

- `--submit-to-metabolights`: By default set to `True`. Will try submit ISA-JSON metadata towards Metabolights. Setting it to `False` will skip sending the ISA-JSON's metadata to Metabolights.

```sh
mars-cli submit --submit-to-metabolights False my-credentials my-isa-json.json
```

`--investigation-is-root`: By default this flag is set to false, maening the ISA-JSON should have the `investigation` key at the root level. In case the root level __IS__ the investigation (`investigation` level is omitted), you need set the flag `--investigation-is-root` to `True` in order to validate the ISA-JSON.

```sh
mars-cli submit --investigation-is-root True my-credentials my-isa-json.json
```

## Validation of the ISA JSON

You can perform a syntactic validation of the ISA-JSON, without submitting to the target repositories.

__Note:__ This does not take validation into account from the repository's side. This does not guarantee successful submission.

```sh
mars-cli validate-isa-json --investigation-is-root True ../test-data/biosamples-input-isa.json
```

### Options

`--investigation-is-root`: By default this flag is set to false, maening the ISA-JSON should have the `investigation` key at the root level. In case the root level __IS__ the investigation (`investigation` level is omitted), you need set the flag `--investigation-is-root` to `True` in order to validate the ISA-JSON.

```sh
mars-cli validate-isa-json my-isa-investigation.json
```

# Extending BioSamples' records
The Python script ``biosamples-externalReferences.py`` defines a class BiosamplesRecord for managing biosample records. This class is designed to interact with the BioSamples database, allowing operations like fetching, updating, and extending biosample records.
The script takes in a dictionary of BioSamples' accessions and their associated external references, and expands the former with the latter.


To summarize, the steps of the code are:
1. Takes the BioSamples' submitter credentials and an input file containing a set of BioSamples accessions and their associated external references
  1. Validates inputs
1. For each BioSamples' accession, it downloads its JSON record from BioSamples
1. Extend the BioSamples' JSON with the ``externalReferences`` of the input file
1. Submit the extended JSON to BioSamples to replace the existing one

## Examples
### BioSamples JSON
Mock example ([``SAMEA112654119``](https://www.ebi.ac.uk/biosamples/samples/SAMEA112654119)):
- Record (JSON) **before** extending with ``externalReferences``:
````
{
  "name" : "AngH91",
  "accession" : "SAMEA112654119",
  ...
}
````
- Record (JSON) **after** extending with ``externalReferences``:
````
{
  "name" : "AngH91",
  "accession" : "SAMEA112654119",
  ...
  "externalReferences" : [ {
    "url" : "https://ega-archive.org/datasets/EGAD00010002458",
    "duo" : [ ]
  }, {
    "url" : "https://ega-archive.org/metadata/v2/samples/EGAN00004248937",
    "duo" : [ ]
  }, {
    "url" : "https://www.ebi.ac.uk/ena/browser/view/SAMEA112654119",
    "duo" : [ ]
  } ]
  ...
}
````
### Script input
In the following example, we would be adding 3 URLs to ``SAMEA112654119`` and one to ``SAMEA419425`` as ``externalReferences``.
````
{
    "biosampleExternalReferences": [
        {
            "biosampleAccession": "SAMEA112654119",
            "externalReferences": [
                {
                    "url": "https://ega-archive.org/datasets/EGAD00010002458"
                },
                {
                    "url": "https://ega-archive.org/metadata/v2/samples/EGAN00004248937"
                },
                {
                    "url": "https://www.ebi.ac.uk/ena/browser/view/SAMEA112654119"
                }
            ]
        },
        {
            "biosampleAccession": "SAMEA419425",
            "externalReferences": [
                {
                    "url": "https://ega-archive.org/datasets/EGAD00010002458"
                }
            ]
        }
    ]
}
````
## Usage
### Command line
````bash
$ python3 biosamples-externalReferences.py --help
usage: biosamples-externalReferences.py [-h] [--production] biosamples_credentials biosamples_externalReferences

This script extends a set of existing Biosamples records with a list of provided external references.

positional arguments:
  biosamples_credentials
                        Either a dictionary or filepath to the BioSamples credentials.
  biosamples_externalReferences
                        Either a dictionary or filepath to the BioSamples' accessions mapping with external references.

options:
  -h, --help            show this help message and exit
  --production          Boolean indicating the usage of the production environment of BioSamples. If not present, the development instance will be used.
````
### Interfacing with BiosamplesRecord Class in Java [_By ChatGPT_]
#### Prerequisites
- **Jython**: A Java implementation of the Python interpreter. It allows running Python code within a Java application.
- **Environment Setup**: Ensure Python and all necessary libraries (``requests``, ``json``, etc.) are installed and accessible to Jython.

#### Basic Steps for Integration
1. **Importing Jython in Java**: Add Jython as a dependency in your Java project.
1. **Executing Python Script**: Use Jython's ``PythonInterpreter`` class to execute the Python script.
1. **Creating BiosamplesRecord Instance**: Instantiate the BiosamplesRecord class through the interpreter.
1. **Interacting with BiosamplesRecord Methods**: Utilize methods like ``fetch_bs_json``, ``extend_externalReferences``, etc., via the interpreter.
1. **Integrating with the Main Function**:
   - The ``main`` function in the script acts as an entry point for command-line usage.
   - In Java, replicate the logic in ``main``.
1. **Data Handling**: Data passed between Java and Python must be in a compatible format (e.g., JSON).
1. **Error Handling**: Properly handle Python exceptions raised by the script in Java.

Sample Java Integration Code:
````java
import org.python.util.PythonInterpreter;
import org.python.core.*;

public class BiosamplesIntegration {
    public static void main(String[] args) {
        PythonInterpreter interpreter = new PythonInterpreter();

        // Load and execute Python script
        interpreter.execfile("path/to/biosamples-externalReferences.py");

        // Create a BiosamplesRecord instance
        PyObject biosamplesRecordClass = interpreter.get("BiosamplesRecord");
        PyObject biosamplesRecord = biosamplesRecordClass.__call__(new PyString("SAMPLE_ACCESSSION"));

        // Use methods of BiosamplesRecord
        PyObject result = biosamplesRecord.invoke("fetch_bs_json", new PyString("biosamples_endpoint"));
        System.out.println(result.toString());

        // Handle other operations similarly
    }
}
````
