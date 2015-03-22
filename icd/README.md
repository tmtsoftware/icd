ICD Validation
==============

This project contains classes and resources for validating ICDs.
The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the ICDs themselves may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format
(see also [Typesafe config](https://github.com/typesafehub/config)).

The JSON Schema `$ref` feature is used to refer to resource files containing JSON schema definitions.
A custom URI handler is defined here that allows you to refer to HOCON format config files from src/main/resources,
which are automatically converted to JSON:
For example:

```
    "$ref" = "config:/publish-schema.conf"
```

refers to resources/publish-schema.conf.

See [json-schema-validator](https://github.com/fge/json-schema-validator/wiki/Features) for other
URI schemes that are supported.

icd Command
-----------

The icd command is generated in target/universal/stage/bin (install.sh copies it to the install/bin directory).
Normal usage is to run the icd command in a directory containing these files:

* icd-model.conf
* component-model.conf
* command-model.conf
* publish-model.conf
* subscribe-model.conf

Example files can be found in the [examples](../examples) directory.

Additional command line options are defined:

```
Usage: icd [options]

  --validate <dir>
        Validates set of files in dir (default: current dir): icd-model.conf, component-model.conf, command-model.conf, publish-model.conf, subscribe-model.conf
  -i <inputFile> | --in <inputFile>
        Single input file to be verified, assumed to be in HOCON (*.conf) or JSON (*.json) format
  -s <jsonSchemaFile> | --schema <jsonSchemaFile>
        JSON schema file to use to validate the single input, assumed to be in HOCON (*.conf) or JSON (*.json) format
  -o <outputFile> | --out <outputFile>
        Saves the ICD to the given file in a format based on the file's suffix (md, html, pdf)
```

Note that for testing you can also convert a single input or schema file to JSON format by combining -i or -s with -o file.json.


Scala API
---------

The [IcdValidator](src/main/scala/csw/services/icd/IcdValidator.scala) class defines a number of
_validate_ methods that take as arguments files, Config objects or directories containing files to validate.

The result of calling validate is a list of Problems. Each Problem includes an error level (warning, error, etc.),
and a string message.

If the document is valid, the list of problems should be empty.
