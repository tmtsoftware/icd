ICD Web App
===========

This subproject contains a Play/Scala.js based web app for accessing the ICD database.

*Note that this project has been tested with java-11 and java-17.

* The icd-web-server subproject is a Play project and provides an HTTP API for the client.

* The icd-web-client project is a Scala.js based client (Scala is automatically compiled into JavaScript)
  (The main client class is [IcdWebClient](../icd-web-client/src/main/scala/icd/web/client/IcdWebClient.scala).)

* The icd-web-shared project contains shared classes that can be used by both client and server

Note that the MongoDB server (mongod) needs to be running on the server machine.
You can add -D options or config file entries to override the default database host, port and name:

* icd.db.host is the host name where mongodb is running (default: localhost)
* icd.db.port is the port for mongodb (default: 27017)
* icd.db.name is the name of the mongodb database (default: icds2)

Usage
-----

To test the app, first make sure MongoDB is running on the localhost on the default port (for now).
For example:

    mongod -dbpath <path>

where <path> is the path in which to store the data.
 
Then start the play web app with the installed `icdwebserver` command, or during development:

    sbt run

in the directory containing build.sbt and open http://localhost:9000/ in a browser.

The subsystem and ICD versions published on GitHub are automatically listed and made available on demand.
Use the *Upload* item to upload your own, unpublished ICD directory (choose for example, examples/NFIRAOS. Do the same for TCS).
It is also possible to upload multiple subsystem or component directories at once. 

Any validation or upload errors or warnings will be displayed in the browser window.

After uploading, click "Select" and select the subsystem (for example, NFIRAOS) from the Subsystem menu to view the API. 
To view an ICD between two subsystems, select a second subsystem as well, or select an ICD
from the ICD menu, if one has been published. Use the arrow button between the two subsystems to
change the order.

Publishing APIs and ICDs
------------------------

After uploading, you have an unpublished API (version = *). 

In order to publish an API or ICD, commit and push your changes to the subsystem's GitHub repository ([under tmt-icd on GitHub](https://github.com/tmt-icd))
and request that they be published. A TMT admin can then use the ICD web app's Publish dialog 
to actually publish the subsystem or ICD version (making an entry in a JSON file under `apis` or `icds` in the
[main repository](https://github.com/tmt-icd/ICD-Model-Files.git)).

Install
-------

Use the install.sh script in the parent directory to install everything into ../../install_icd/.
After that, you can use the `icdwebserver` command to start the web app on the server.
By default this starts the server on http://localhost:9000, however you can override the HTTP host and port on
the command line. For example:

    icdwebserver -Dhttp.port=9898 -Dhttp.host=192.168.178.77
