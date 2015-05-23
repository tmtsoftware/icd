ICD - Interface Control Document Management
===========================================

This project contains support for validating, storing, managing, 
searching and viewing ICDs (Interface Control Documents between TMT subsystems or components).
The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the ICDs themselves may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format.

ICDs are stored in a MongoDB database, which also keeps track of any changes made.
Two command line applications ([icd](icd) and [icd-db](icd-db)) and a web app ([icd-web](icd-web)) 
are provided for ingesting the ICDs from files, querying and viewing the data.

ICD Subprojects
---------------

There are currently three ICD subprojects (icd-web has a separate build):

* icd - supports validating an ICD against the JSON schema as well as saving it as a Markdown, HTML or PDF document
* icd-db - supports ingesting an ICD into a MongoDB database, querying the db and saving an ICD as a document
* icd-web - a Play/Scala.js based web app for working with ICDs (separate build.sbt, depends on icd-db project)

Build and Install
-----------------

An install.sh script is provided that builds and installs all of the subprojects into the ../install directory.
This is basically just the command `sbt stage` in each project followed by copying the products to the
install directory. A binary is installed for each subproject, with the same name as the subproject
(except for icd-web, where the binary produced is `icdwebserver`).

For the two command line apps (icd and icd-db), type `icd --help` or `icd-db --help` for a list of the 
command line options.

The icdwebserver application starts the web app (by default on localhost:9000).

Play Project icd-web
--------------------

The icd-web project has its own build file. To test, run `sbt run` from the icd-web directory.
Then go to http://localhost:9000 in a web browser.





