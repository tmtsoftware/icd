ICD Validation
==============

This project contains classes and resources for validating ICDs.
The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the ICDs themselves may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format,
or more exactly, the [Typesafe config](https://github.com/typesafehub/config) format.

The JSON Schema `$ref` feature is used to refer to resource files containing JSON schema definitions.
A custom URI is defined here that allows you to refer to HOCON format config files,
which are automatically converted to JSON:
For example:

```
    "$ref" = "config:/publish-schema.conf"
```

refers to resources/publish-schema.conf.

See [json-schema-validator](https://github.com/fge/json-schema-validator/wiki/Features) for other
URI schemes that are supported.

icd Command
===========

The icd command is generated in target/universal/stage/bin.

```
Usage: icd [options]

  -i <inputFile> | --in <inputFile>
        Input file to be verified, assumed to be in HOCON (*.conf) or JSON (*.json) format

  -s <jsonSchemaFile> | --schema <jsonSchemaFile>
        JSON schema file to use to validate the input, assumed to be in HOCON (*.conf) or JSON (*.json) format

  -o <jsonOutputFile> | --out <jsonOutputFile>
        Save the input file (or the schema file, if no input file was given) in JSON format (for testing)
```

For example, cd to the directory containing the icd command (csw/icd/target/universal/stage/bin):

```
-> ./icd -i ../../../../src/test/resources/icd-good1.conf -s ../../../../src/main/resources/icd-schema.conf

-> ./icd -i ../../../../src/test/resources/icd-bad1.conf -s ../../../../src/main/resources/icd-schema.conf
error: instance value ("Nope") not found in enum (possible values: ["Yes","No"])
```

In the first case, no errors were found. In the second case, the error is displayed.


Scala API
=========

There are two versions of `IcdValidator.validate`. One takes an input file and a schema file to use to
validate it. The other takes an input Config and a schema Config object (for example, from a resource config file).
The files can be in HOCON or JSON format. HOCON formatted files are automatically converted to JSON.

The result of calling validate is a list of Problems. Each Problem includes an error level (warning, error, etc.),
a string message and an additional string in JSON format that includes more details about the error.

If the document is valid, the list of problems should be empty.
