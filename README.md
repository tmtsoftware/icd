ICD - Interface Control Document Management
===========================================

This project contains support for validating, 
searching and viewing subsystem APIs and ICDs (Interface Control Document between two TMT subsystems).

The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the ICDs themselves may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format.

Versions of ICDs are managed in [GitHub repositories](https://github.com/tmt-icd/ICD-Model-Files.git) and 
the subsystem model files can be imported (with version history) into a local MongoDB database, which is used
by command line applications and a web app.
 
Three command line applications ([icd](icd), [icd-db](icd-db)), [icd-git](icd-git)) and a web app ([icd-web](icd-web)) 
are provided for working with ICDs, querying and viewing the data.

The applications here assume the MongoDB database is running. 
To start the MongoDB server, you can run a command like this:

    mongod -dbpath $db
    
where $db is the directory containing the database.

The default database name used is `icds` and can be configured in icd-db/src/main/resources/reference.conf,
in <installDir>/conf/reference.conf or via equivalent -D (system property) command line options.


ICD Subprojects
---------------

There are currently these ICD subprojects:

* [icd](icd) - supports validating an API against the JSON schema as well as saving it as a Markdown, HTML or PDF document
* [icd-db](icd-db) - supports ingesting API files into a MongoDB database, querying the db and saving an API or ICD as a document
* [icd-git](icd-git) - work directly with ICD model files stored on GitHub, publish ICDs, ingest ICD releases into the ICD database
* [icd-web-server](icd-web-server) - a Play web server for working with ICDs
* [icd-web-client](icd-web-client) - a Scala.JS based web client for the Play server
                                     (The main client class is [IcdWebClient](icd-web-client/src/main/scala/icd/web/client/IcdWebClient.scala).
* [icd-web-shared](icd-web-shared) - contains shared classes that can be used by both web client and server

Build and Install
-----------------

Note: The build requires that [node.js](https://nodejs.org/en/) be installed on the system
and this environment variable set:

    SBT_OPTS=-Dsbt.jse.engineType=Node -Dsbt.jse.command=/usr/bin/node

(Change `node` to `nodejs` if that is the name of the executable on your system.) 

This is checked in the install.sh script, which automatically sets the SBT_OPTS environment variable if node.js is found 
and gives an error otherwise. 

An install.sh script is provided that builds and installs all of the subprojects into the ../install_icd directory.
This is basically just the command `sbt stage` in each project followed by copying the products to the
install directory. A binary is installed for each subproject, with the same name as the subproject
(except for icd-web, where the binary produced is `icdwebserver`).

For the command line apps, the `--help` option prints a summary of the command line options.

The `icdwebserver` application starts the web app (by default on localhost:9000).
You can change the port used by adding an option like this: `-Dhttp.port=9876`.

Note that the build is set up so that the Play subproject is selected on start.
So to run any commands (like sbt clean or sbt stage) that should apply to the other projects,
you need to first switch to that project or the root project. For example `sbt clean "project root" clean`. 

Play Project icd-web-server
---------------------------

To start the server for the web app during development, you can use `sbt run` from this directory.
Then go to http://localhost:9000 in a web browser.

See [icd-web-server/README.md](icd-web-server/README.md) for more information.

Importing ICD-Model-Files from GitHub into the ICD Database with Version History
--------------------------------------------------------------------------------

Using the [icd-git](icd-git) command line application you can publish subsystem APIs and ICDs between subsystems.
Publishing a subsystem or ICD adds an entry to a JSON file on GitHub which is used later to extract specific 
versions of the model files.

The app also lets you import subsystem model files directly from the
[GitHub repositories](https://github.com/tmt-icd/ICD-Model-Files)  into a local MongoDB database
for use with the icd tools. 

Warning: The icd-git app will currently delete the current contents of the ICD database before
ingesting the files from the repository (*This may be changed in the future*).

The icd web app lists the published versions of subsystems and ICDs from GitHub and the model
files are checked out and ingested into the database automatoically as needed (when you select a subsystem 
from the menu, for example).

Known Issues
------------

Including a comma in an unquoted string in an ICD model file can cause a NullPointerException.
For example, don't do this: 

    description = The beam-splitter stage has an unacceptable position error, datum may be lost.

This is a known bug in the Config class: See https://github.com/lightbend/config/issues/367.

Docker Install
--------------

Two Docker related scripts are provided in the top level directory.

* docker-build.sh - Runs `sbt docker:stage` and `docker build` to build the docker image

* docker-run.sh - Can be used to run the ICD web server inside Docker

__Note__ that *both* scripts should be edited to add the correct docker user.
See comments in the scripts for more information.




