ICD Database
============

This project provides the ICD database interface and command line application, based on MongoDB.
It is assumed that the MongoDB server is running on the given (or default) host and port.

icd-db Command
==============

The icd-db command is generated in target/universal/stage/bin.
Normal usage is to run the icd-db command in a directory containing these files:

* icd-model.conf
* component-model.conf
* command-model.conf
* publish-model.conf
* subscribe-model.conf

Example files can be found in [examples](../examples) directory.

```
Usage: icd-db [options]

  -d <name> | --db <name>
        The name of the database to use (default: icds)
  -h <hostname> | --host <hostname>
        The host name where the database is running (default: localhost)
  -p <number> | --port <number>
        The port number to use for the database (default: 27017)
  -i <dir> | --ingest <dir>
        Directory containing ICD files to ingest into the database
  -l [hcds|assemblies|all] | --list [hcds|assemblies|all]
        Prints a list of hcds, assemblies, or all components
  -c <name> | --component <name>
        Specifies the component to be used by any following options
  -o <outputFile> | --out <outputFile>
        Saves the component's ICD to the given file in a format based on the file's suffix (md, html, pdf)
```

