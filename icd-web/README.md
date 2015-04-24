ICD Web App
===========

This project contains a Play/Scala.JS based web app for accessing the ICD database.
It also makes use of Bootstrap and Less.

* The icd-web-server subproject is a Play project and provides a REST interface for the client.

* The icd-web-client project is a Scala.JS based client (Scala is automatically compiled into JavaScript)

* The icd-web-shared project contains shared classes that can be used by both client and server

Note that the MongoDB server (mongod) needs to be running on the server machine.

Temporary info
--------------

* Note: Until the *Upload* feature is implemented, you will need to use the icd-db command line app to
  put some ICDs in the database in order to test this.
  
* Currently, the database name used is "test", but this will be configurable at some point





