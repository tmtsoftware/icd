ICD Validation
==============

This project contains classes and resources for validating ICDs.
The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the ICDs themselves may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format,
or more exactly, the [Typesafe config](https://github.com/typesafehub/config) format.

The schema config files make use of the `include` feature to reference other schemas.
This is a typesafe config feature and is used to avoid having large, complicated schema files.

The JSON Schema `$ref` feature is then used to refer to the included definitions. For example:

```
    "$ref" = "#/definitions/publish"
```

refers to the definitions/publish section, which included the publish schema.


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



