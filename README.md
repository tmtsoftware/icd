# TIO Software Interface Database System

Acronyms: 
* TMT: Thirty Meter Telescope
* TIO: TMT International Observatory
* ICD: Interface Control Document (between two TIO subsystems)
* API: Application Programming Interface
* IDBS: Interface Database System
* JSON: JavaScript Object Notation
* HOCON: Human-Optimized Config Object Notation (simplified JSON)

This project contains support for creating, validating and viewing TIO subsystem APIs and ICDs.

*You can find a detailed description of the IDBS software [here](https://tmtsoftware.github.io/idbs/webapp/webapp.html).*

Subsystem APIs are described in model files. The model files are validated using [JSON Schema](http://json-schema.org/),
however the schema descriptions as well as the model files are normally written in
the simpler [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format (.conf suffix).

Model files with the `.jsonnet` suffix are assumed to be in [jsonnet](https://jsonnet.org/) format and 
are preprocessed to produce the JSON (see [this example](examples/3.0/TEST/jsonnet-example)).

(See [JsonSchemaChanges.md](JsonSchemaChanges.md) for a list of recent changes in the JSON Schema for API model files.)

In addition, HTTP services can be described using [OpenAPI](https://swagger.io/specification/) files
(See [example Segment Service](examples/3.0/TEST2/segmentService)).

Versions of APIs and ICDs are managed in [GitHub repositories](https://github.com/tmt-icd/ICD-Model-Files.git) and 
the subsystem model files can be imported from GitHub (with version history) into a local MongoDB database, which is used
by command line applications and a web app.

The [examples](examples) directory also contains some example API descriptions in both the old and new schema versions. All schema versions are supported for backward compatibility, however the newest version (3.0) should be used for future work. To help in upgrading existing subsystem APIs, branches named `schema-3.0` have been created on GitHub for the existing subsystems. 
 
Command line applications: [icd-db](icd-db), [icd-fits](icd-db/src/main/scala/csw/services/icd/fits), 
[icd-git](icd-db/src/main/scala/csw/services/icd/github) and a web app ([icdwebserver](icd-web-server)) 
are provided for working with APIs and ICDs, querying and viewing the data.

The applications here assume the [MongoDB database](https://www.mongodb.com) is running. 
To start the MongoDB server, you can run a command like this:

    mongod -dbpath $db
    
where $db is the directory that contains (or should contain) the database.
Mongodb can also be started automatically at boot time.

After starting the database, ingest the published ICDs, which are stored in GitHub repositories:

    icd-git --ingest  # Loads the latest published APIs and ICDs into the local icd database

or

    icd-git --ingestAll  # loads all published APIs and ICDs into the local icd database

You can rerun this command occasionally to get any updates from newly published ICDs or APIs (The icd web app automatically ingests any missing APIs and ICDs when started).

ICD Subprojects
---------------

There are currently these ICD subprojects:

* [icd-db](icd-db) - supports ingesting API model files into a MongoDB database, publishing and querying the db and saving an API or ICD as an HTML or PDF document
* [icd-web-server](icd-web-server) - a Play Framework based web server for working with ICDs
* [icd-web-client](icd-web-client) - a Scala.js based web client for the Play server (The main client class is [IcdWebClient](icd-web-client/src/main/scala/icd/web/client/IcdWebClient.scala)).
* [icd-web-shared](icd-web-shared) - contains shared classes that can be used by both web client and server

Build and Install
-----------------
*Note that this project has been tested with java-21 and scala-3.

Note: The build requires that [node.js](https://nodejs.org/en/) be installed on the system.
This is checked in the install.sh script, which automatically sets the SBT_OPTS environment variable if node.js is found 
and gives an error otherwise. 

Note: 
- The [Graphviz](https://graphviz.org/download/) apps needs to be installed in order to use the UML or icd-viz features (Tested with version 2.43.0).
- The [jsonnet](https://jsonnet.org/) command needs to be installed to use sjsnonnet syntax for model files (Tested with version 0.17.0).
- The [swagger-codegen](https://swagger.io/tools/swagger-codegen/) command line app needs to be installed in order to generate the documentation for components that provide or use HTTP services and declare them in the `service-model.conf` model file. You can install `swagger-codegen` with this command: `cs install --contrib swagger-codegen`. This icd release was tested with version swagger-codegen-3.0.36.

An install.sh script is provided that builds and installs all of the subprojects into the __../install_icd__ directory.
This is basically just the command `sbt stage` in each project followed by copying the products to the
install directory. A binary is installed for each subproject, with the same name as the subproject.

For the command line apps, the `--help` option prints a summary of the command line options.

The `icdwebserver` application starts the web app (by default on localhost:9000).
You can change the port used by adding an option like this: `-Dhttp.port=9876`.

ICD Web App
------------

To start the ICD web app on http://localhost:9000/, run `icdwebserver`,
then go to http://localhost:9000 in a web browser.

To run as a public server, edit the provided script, [icd-wed-server.sh](icd-wed-server.sh), and change the
settings (certificate, etc.) as needed for the server. 

Note that the `-Dicd.isPublicServer=true` option hides the *upload* feature, which allows
users to test their local changes before publishing. This option also makes the Publish tab visible.

Note that the first time you start `icdwebserver`, it will update the ICD database from the released versions on GitHub, 
which can take some time.

To start the web app with continuous compilation during development, you can type `~icdWebServer/run` from the sbt shell.

See [icd-web-server/README.md](icd-web-server/README.md) for more information.

Importing ICD-Model-Files from GitHub into the ICD Database with Version History (Using the Command Line Tools)
------------------------------------------------------------------------------------------------------------------

The icd-git command line app lets you import subsystem model files directly from the
[GitHub subsystem model file repositories](https://github.com/tmt-icd/)  into a local MongoDB database
for use with the icd tools. For example, to ingest all the published APIs and ICDS into the local database, use:

    icd-git --ingestAll

To load only the latest published versions, use:

    icd-git --ingest

Note: The icd-git app will currently delete the current contents of the local ICD database before
ingesting the files from the repository.

The icd web app lists the published versions of subsystem APIs and ICDs from GitHub and the model
files are checked out and ingested into the database automatically as needed the *first time* you start the web app. 

FITS Keywords
-------------

The generated subsystem APIs contain infomation about FITS keywords whose values come from event parameters.
That is, an event's parameter value is the `source` of the FITS keyword's value.
FITS keyword data is stored in three files under [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files/tree/master/FITS-Dictionary)
on GitHub. Once DMS is published, the file should be automatically loaded by the `icdwebserver` or `icd-git --ingest` commands.
Until then, the FITS keywords, `channels` and `tags` can be manually loaded into the icd database once by running (from this directory):

```
icd-fits -i examples/3.0/FITS-Dictionary.json --ingestChannels examples/3.0/FITS-Channels.conf --ingestTags examples/3.0/FITS-Tags.conf
```

Alternatively you can check out and manually ingest [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files/tree/master/FITS-Dictionary)
into the local icd database by using the `Upload` feature in the icd web app or with the command line:

```
    icd-db -i DMS-Model-Files
```

The contents of the files are as follows:

* FITS-Dictionary.json - This is the FITS dictionary and contains an entry for each FITS keyword, mapping it to `source` event parameters. If a keyword has multiple sources, named `channels` are used, each containing one source.
* FITS-Channels.conf - This defines which channels are available for each subsystem (Channels are used when a FITS keyword has multiple source event parameters)
* FITS-Tags.conf - assigns tags to FITS keywords, which can be used in the web app to filter and display the FITS keyword information.

Besides the three above files, FITS keyword information can be defined in the publish-model.conf files for each subsystem component. 
Event parameters can define the associated keyword as follows:

```
        keyword = IMGDISWV
```

If the keyword has multiple source parameters, you can specify the channel:

```
        keyword = IMGDISWV
        channel = ATM
```

In some more complicated cases, you can also specify multiple keywords whose values are taken from an `index` (or `rowIndex` for matrix/2d arrays) in the parameter's array values:

```
          keywords: [
            {
              keyword = OIWFS1PS
              rowIndex = 0
            }
            {
              keyword = OIWFS2PS
              rowIndex = 1
            }
            {
              keyword = OIWFS3PS
              rowIndex = 2
            }
          ]
```

The FITS keyword definitions in a subsystem's model files can be used to generate a new FITS dictionary by merging the existing 
FITS dictionary with the definitions in the publish model files. In this case the information from the published events overrides 
the information in the existing FITS dictionary:

```
    icd-fits --subsystem IRIS --generate FITS-Dictionary.json
```

Or using the short form options and with a subsystem version: 

```
    icd-fits -s IRIS:1.7 -g FITS-Dictionary.json
```

The generated FITS dictionary JSON file can then be copied to the [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files/tree/master/FITS-Dictionary)
repository and published, so that it will be automatically used by the icd web app and command line apps.
You can also manually load the new FITS dictionary into your local icd database using the command line:

```
    icd-fits -i FITS-Dictionary.json
```


Known Issues
------------

* Most Unix-like operating systems limit the system resources that a session may use. 
  These limits negatively impact MongoDB operation. 
  See [UNIX ulimit Settings](https://docs.mongodb.com/manual/reference/ulimit/) for more information.
  csh/tcsh users should run the command `unlimit` before starting `mongod`.
* For MacOS, it can be a bit more complicated: See [here](MacOs-Max-Files-Limit.md) for more info.

* Including a comma in an unquoted string in an ICD model file can cause a NullPointerException.
For example, don't do this: 
```
description = The beam-splitter stage has an unacceptable position error, datum may be lost.
```
This is a known bug in the Config class: See https://github.com/lightbend/config/issues/367.
In general, it is safer to put description text in double quotes.

Tips
----

See the [examples/3.0](examples/3.0) directory for some example model files.
The exact syntax is defined by JSON-Schema files in [src/main/resources/3.0](src/main/resources/3.0).

Inner-Document Links
--------------------

It is possible to make inner-document links to existing anchors using Markdown syntax.
The easiest way to see the syntax for the ids is to look at the generated HTML.
For example, the output of:

    icd-db -s NFIRAOS -o NFIRAOS.html

(Note that the `name` attribute is used in the generated HTML instead of `id`, since the PDF generator requires that.)
Many of the anchors have the following syntax:

    $thisComponent-$action-$itemType-$subsystem.$component.$name

where 

* `$thisComponent` is the component being described
* `$action` is one of {publishes, subscribes, sends, receives}
* `$itemType` is one of {Event, ObserveEvent, Alarm, Command}
* `$subsystem` is the subsystem for the item
* `$component` is the component for the item
* `$name` is the name of the item being published, subscribed to, or the command being sent or received

For example, to link to the description of a published event named heartbeat in the lgsWfs component in the TEST subsystem:

    See: [Example document internal link](#TEST.lgsWfs-publishes-Event-heartbeat).
 
Reusing Event, Command and Parameter Definitions
-------------------------------------------------

It is possible to reuse similar parts of event, command and parameter definitions by using the "ref" keyword. For example:

```
  events = [
    {
      name = engMode
      description = "LGS WFS engineering mode enabled"
      archive = false
      parameters = [
        ...
      ]
    }
    {
      name = engMode2
      description = "LGS WFS engineering mode 2 enabled"
      archive = true
      ref = engMode
    }
```
In the above example, the event `engMode2` will have the same settings and parameters as `engMode`, except for `description` and `archive`, which are overridden. Any fields which are not set, are inherited from the referenced event.

This works for events, commands and parameters, as show below:

```
      parameters = [
        {
          name = mode3
          ref = engMode/parameters/mode
        }

```
In the above example, the parameter `mode3` will be exactly the same as the `mode` parameter in the engMode event in the same component. You could also specify a different `description` field or any other parameter fields that should override the ones defined for `mode`.

The syntax of the `ref` value is flexible and allows you to reference any event, command or parameter in any component within the same subsystem. You can use a full path to specify a reference to an item in another component, or an abbreviated path for items in the same scope. The full syntax of a `ref` is something like this:
```
$componentName/$section/$eventName[/parametersSection/$paramName]
``` 
For example, to reference an event, observe event or current state, use:
```
$componentName/events/$eventName
or $componentName/observeEvents/$eventName
or $componentName/currentState/$eventName
or events/$eventName, ... (if in the same component)
or just $eventName (if in the same component and event type section)
```    

For commands received, the syntax is similar:
```
$componentName/receive/$commandName
or just $commandName (if in the same component)
```

The syntax for references to parameters of events adds the `parameters` keyword and the parameter name:
```
$componentName/events/$eventName/parameters/$paramName
or abbreviated as above:
observeEvents/$eventName/parameters/$paramName (in same component)
or $eventName/parameters/$paramName (in same component and events section)
or just $paramName (if in the same parameters section)
```

The syntax for parameters of commands is similar. 
Here you need to specify if the parameters appear in the `parameters` section or in the `resultType`.
```
$componentName/receive/$commandName/parameters/$paramName
or $componentName/receive/$commandName/resultType/$paramName
or abbreviated as above.
```

See the example model files in [src/main/resources/3.0](src/main/resources/3.0) for some examples of the `ref` keyword.

Note that if there is an error in the reference, the error message is displayed in the log output of the icd-db command, if it is used, and also in the generated HTML or PDF document (in the details section).

Note: An earlier version of this software used the terms "attributes" for events parameters and "args" for command parameters. 
These have been renamed to "parameters" for compatibility with CSW, however for backward compatibility
the previous names are also allowed in refs.

Event Categories
================

Events have an optional "category" field, which can have one of the following values:

* DEMAND - an event that is used to transmit a desired position. These events are high frequency/periodic and should not be archived long-term or should be seriously curated into a smaller representative collection.

* CONTROL - similar to a DEMAND, but probably not periodic and considerably less frequent. CONTROL events are events driving other devices, but may be internal to a system. These also may be curated.

* EVENT - an event is used to indicate that something has happened.  Observe Events are one EVENT type.

* STATUS - a STATUS  event is used primarily to update a user interface.  These events are archived.  They are not high frequency and are not periodic.
