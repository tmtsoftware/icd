ICD Projects
============

There are currently two ICD subprojects:

* icd - supports validating an ICD against the JSON schema as well as saving it as a Markdown, HTML or PDF document
* icd-db - supports ingesting an ICD into the database, querying the db and saving an ICD as a document
* icd-web - ICD web app (TBD)

Build and Install
-----------------

An install.sh script is provided that builds and installs all of the subprojects into the ../install directory.
This is basically just the command `sbt stage "project icd-web" stage` followed by copying the products to the
install directory. A binary is installed for each subproject, with the same name as the subproject.
Type `icd --help` or `icd-db --help` for a list of the command line options.

Play Project icd-web
--------------------

The icd-web project is handled specially, since it is a Play project.
You can run the web server in the development environment with `sbt "project icd-web" run`.
This starts a web server on http://localhost:9000.




