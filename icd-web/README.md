ICD Web App
===========

This project contains a Play/Scala.js based web app for accessing the ICD database.
It also makes use of Bootstrap and Less.

* The icd-web-server subproject is a Play project and provides a REST interface for the client.

* The icd-web-client project is a Scala.JS based client (Scala is automatically compiled into JavaScript)

* The icd-web-shared project contains shared classes that can be used by both client and server

Note that the MongoDB server (mongod) needs to be running on the server machine.

Usage
-----

To test the app, first make sure MongoDB is running on the localhost on the default port (for now).
For example:

    mongod -dbpath <path>

where <path> is the path in which to store the data.
 
Then start the play web app with:

    sbt run

in this directory and open http://localhost:9000/ in a browser.
Use the *Upload* item to upload an ICD (choose examples/NFIRAOS, if you are using Chrome, otherwise 
make a zip file of that directory and upload that).

Any validation or upload errors or warnings will be displayed in the browser window.

After uploading, select NFIRAOS from the Subsystem menu. Then you can use the View menu to view the ICD
or click on one of the components in the sidebar at left to view publish/subscribe information for the component
(*Work in progress!*).






