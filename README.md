ICD - Interface Control Document Management
===========================================

This project contains support for creating, validating and viewing subsystem APIs and ICDs (Interface Control Document between two TMT subsystems).

The validation is based on [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the model files may also be written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format.

Versions of APIs and ICDs are managed in [GitHub repositories](https://github.com/tmt-icd/ICD-Model-Files.git) and 
the subsystem model files can be imported from GitHub (with version history) into a local MongoDB database, which is used
by command line applications and a web app.
 
Command line applications: [icd-db](icd-db), [icd-git](icd-git) and a web app ([icdwebserver](icd-web-server)) 
are provided for working with APIs and ICDs, querying and viewing the data.

The applications here assume the [MongoDB database](https://www.mongodb.com) is running. 
To start the MongoDB server, you can run a command like this:

    mongod -dbpath $db
    
where $db is the directory that contains (or should contain) the database.

After starting the database, ingest the published ICDs, which are stored in GitHub repositories:

    icd-git --ingest

You should rerun this command occasionally to get any updates from newly published ICDs or APIs.

ICD Subprojects
---------------

There are currently these ICD subprojects:

* [icd-db](icd-db) - supports ingesting API model files into a MongoDB database, querying the db and saving an API or ICD as an HTML or PDF document
* [icd-git](icd-git) - work directly with ICD model files stored on GitHub, publish ICDs, ingest ICD releases into the ICD database
* [icd-web-server](icd-web-server) - a Play Framework based web server for working with ICDs
* [icd-web-client](icd-web-client) - a Scala.JS based web client for the Play server
                                     (The main client class is [IcdWebClient](icd-web-client/src/main/scala/icd/web/client/IcdWebClient.scala).
* [icd-web-shared](icd-web-shared) - contains shared classes that can be used by both web client and server

Build and Install
-----------------

Note: The build requires that [node.js](https://nodejs.org/en/) be installed on the system.
This is checked in the install.sh script, which automatically sets the SBT_OPTS environment variable if node.js is found 
and gives an error otherwise. 

An install.sh script is provided that builds and installs all of the subprojects into the __../install_icd__ directory.
This is basically just the command `sbt stage` in each project followed by copying the products to the
install directory. A binary is installed for each subproject, with the same name as the subproject.

For the command line apps, the `--help` option prints a summary of the command line options.

The `icdwebserver` application starts the web app (by default on localhost:9000).
You can change the port used by adding an option like this: `-Dhttp.port=9876`.

Note that the build is set up so that the Play subproject is selected on start.
So to run any commands (like sbt clean or sbt stage) that should apply to the other projects,
you need to first switch to that project or the root project. For example `sbt clean "project root" clean`. 

ICD Web App
------------

To start the ICD web app on http://localhost:9000/, run `icdwebserver`,
then go to http://localhost:9000 in a web browser.

To run as a public server, you should use something like this to start it:

    icdwebserver -Dicd.allowUpload=false -Dhttp.host=$hostname -Dhttp.port=8080

and then go to http://$hostname:8080. The `-Dicd.allowUpload=false` option hides the *upload* feature, which allows
users to test their changes before publishing.

Note that the first time you start `icdwebserver`, it will update the ICD database from the released versions on GitHub. 

To start the web app with continuous compilation during development, you can use `sbt ~run` from this directory.

See [icd-web-server/README.md](icd-web-server/README.md) for more information.

Importing ICD-Model-Files from GitHub into the ICD Database with Version History
--------------------------------------------------------------------------------

Using the [icd-git](icd-git) command line application you can publish subsystem APIs and ICDs between subsystems 
(assuming you have the necessary access to the [GitHub repository](https://github.com/tmt-icd/ICD-Model-Files)).
Publishing a subsystem API or ICD adds an entry to a JSON file on GitHub which is used later to extract specific 
versions of the model files.

The app also lets you import subsystem model files directly from the
[GitHub subsystem model file repositories](https://github.com/tmt-icd/)  into a local MongoDB database
for use with the icd tools. For example, to ingest all the published APIs and ICDS into the local database, use:

    icd-git --ingest

Note: The icd-git app will currently delete the current contents of the local ICD database before
ingesting the files from the repository.

The icd web app lists the published versions of subsystem APIs and ICDs from GitHub and the model
files are checked out and ingested into the database automatically as needed the *first time* you start the web app. 

Known Issues
------------

* csh/tcsh users should run the command `unlimit` or add `unlimit` to your .cshrc or .tcshrc file before starting `mongod` to make sure
  it has access to enough file descriptors.

* Including a comma in an unquoted string in an ICD model file can cause a NullPointerException.
For example, don't do this: 
```
description = The beam-splitter stage has an unacceptable position error, datum may be lost.
```
This is a known bug in the Config class: See https://github.com/lightbend/config/issues/367.
In general, it is safer to put description text in double quotes.

Docker Install
--------------

Two Docker related scripts are provided in the top level directory.

* docker-build.sh - Runs `sbt docker:stage` and `docker build` to build the docker image

* docker-run.sh - Can be used to run the ICD web server inside Docker

__Note__ that *both* scripts should be edited to add the correct docker user.
See comments in the scripts for more information.




