# Installing the mars-cli

Installing the mars-cli from source:

```sh
cd mars-cli # Assuming you are in the root folder
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
