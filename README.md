ICD - Interface Control Document Management
===========================================

This project contains support for validating, storing, managing, 
searching and viewing APIs and ICDs (Interface Control Documents between TMT subsystems).
An ICD consists of source and target subsystem APIs.
The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the ICDs themselves may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format.

ICDs are stored in a MongoDB database, which also keeps track of any changes made.
Two command line applications ([icd](icd) and [icd-db](icd-db)) and a web app ([icd-web](icd-web)) 
are provided for ingesting the ICDs from files, querying and viewing the data.

The applications here assume the MongoDB database is running. 
To start the MongoDB server, you can run a command like this:

    mongod -dbpath $db
    
where $db is the directory containing the database.

The default database name used is `icds` and can be configured in icd-db/src/main/resources/reference.conf
(or in <installDir>/conf/reference.conf).


ICD Subprojects
---------------

There are currently these ICD subprojects:

* icd - supports validating an API against the JSON schema as well as saving it as a Markdown, HTML or PDF document
* icd-db - supports ingesting API files into a MongoDB database, querying the db and saving an API or ICD as a document
* icd-web/* - a Play/Scala.js based web app for working with ICDs

Build and Install
-----------------

An install.sh script is provided that builds and installs all of the subprojects into the ../install_icd directory.
This is basically just the command `sbt stage` in each project followed by copying the products to the
install directory. A binary is installed for each subproject, with the same name as the subproject
(except for icd-web, where the binary produced is `icdwebserver`).

For the two command line apps (icd and icd-db), type `icd --help` or `icd-db --help` for a list of the 
command line options.

The icdwebserver application starts the web app (by default on localhost:9000).

Note that the build is set up so that the Play subproject is selected on start.
So to run any commands (like sbt clean or sbt stage) that should apply to the other projects,
you need to first switch to that project or the root project. For example `sbt clean "project root" clean`. 

Play Project icd-web
--------------------

To test the web server, run `sbt run` from this directory.
Then go to http://localhost:9000 in a web browser.

See [icd-web/README.md](icd-web/README.md) for more information.

Importing ICD-Model-Files from GitHub into the ICD Database with Version History
--------------------------------------------------------------------------------

Using the icd-ingest.sh script in this directory you can import all of the subsystem model files from the
[ICD-Model-Files](https://github.com/tmtsoftware/ICD-Model-Files) GitHub repository into a local MongoDB database
for use with the icd tools. For this to work, you must have installed the icd software
(run the install.sh script in this project). The icd-ingest.sh script assumes the default install directory:
../install_icd.
MongoDB must be running and a recent version of git must be installed.

Warning: The icd-ingest.sh script will delete the current contents of the ICD database before
ingesting the files from the repository.

The icd software looks for release tags in the Git submodules.
Git subsystem releases should have names like "v1.0", "v1.2", "v2.0", etc.
These then translate into published versions in the ICD database: "1.0", "1.1", "2.0".

Docker Install
--------------

Two Docker related scripts are provided in the top level directory.

* docker-build.sh - Runs `sbt docker:stage` and `docker build` to build the docker image

* docker-run.sh - Can be used to run the ICD web server inside Docker

__Note__ that *both* scripts should be edited to add the correct docker user.
See comments in the scripts for more information.







