ICD Database
============

This project provides the ICD database interface and command line application, based on MongoDB.
It is assumed that the MongoDB server is running on the given (or default) host and port.

To start the MongoDB server, you can run a command like this:

    mongod -dbpath $db
    
where $db is the directory thath contains (or should contain) the database.
See [here](https://docs.mongodb.com/manual/administration/install-community/) for more information about installing
and running MongoDB.

The database name used to store API information is configured in src/main/resources/reference.conf
(The test cases use a different database).

icd-db Command
--------------

The icd-db command is generated in target/universal/stage/bin (install.sh copies it to the install/bin directory).

Example files that can be ingested into the database for testing can be found
in the [examples/2.0](../examples/2.0) directory.

```
icd-db 2.0.0
Usage: icd-db [options]

  -d, --db <name>          The name of the database to use (default: icds4)
  -h, --host <hostname>    The host name where the database is running (default: localhost)
  -p, --port <number>      The port number to use for the database (default: 27017)
  -i, --ingest <dir>       Top level directory containing files to ingest into the database
  -l, --list [subsystems|assemblies|hcds|all]
                           Prints a list of ICD subsystems, assemblies, HCDs or all components
  --listData <subsystem>   Prints a list of event sizes and yearly accumulation of archived data for components of the specified subsystem.
  -u, --allUnits           Prints the set of unique units used in all received commands and published events for all components in DB.
  -c, --component <name>   Specifies the component to be used by any following options (subsystem must also be specified)
  -s, --subsystem <subsystem>[:version]
                           Specifies the subsystem (and optional version) to be used by any following options
  -t, --subsystem2 <subsystem>[:version]
                           Specifies the second subsystem (and optional version) in an ICD to be used by any following options
  --component2 <name>      Specifies the subsytem2 component to be used by any following options (subsystem2 must also be specified)
  --icdversion <icd-version>
                           Specifies the version to be used by any following options (overrides subsystem and subsystem2 versions)
  -o, --out <outputFile>   Saves the selected API or ICD to the given file in a format based on the file's suffix (html, pdf)
  --drop [db|subsystem|component]
                           Drops the specified component, subsystem, or the entire icd database (requires restart of icd web app)
  --versions <subsystem>   List the version history of the given subsystem
  --diff <subsystem>:<version1>[,version2]
                           For the given subsystem, list the differences between <version1> and <version2> (or the current version)
  -m, --missing <outputFile>
                           Generates a 'Missing Items' report to the given file (dir for csv) in a format based on the file's suffix (html, pdf, otherwise text/csv formatted files are generated in given dir)
  -a, --archived <outputFile>
                           Generates an 'Archived Items' report for all subsystems (or the given one) to the given file in a format based on the file's suffix (html, pdf)
  --allSubsystems          Include all subsystems in searches for publishers, subscribers, etc. while generating API doc (Default: only consider the one subsystem)
  --clientApi              Include subscribed events and sent commands in the API dic (Default: only include published events and received commands)
  --orientation [portrait|landscape]
                           For PDF output: The page orientation (default: landscape)
  --fontSize <size>        For PDF or HTML file output: The base font size in px for body text (default: 10)
  --lineHeight <height>    For PDF or HTML file output: The line height (default: 1.6)
  --paperSize [Letter|Legal|A4|A3]
                           For PDF output: The paper size (default: Letter)
  --help                   
  --version                
```

Example:
--------

```
> icd-db --ingest examples/NFIRAOS/
> icd-db --list all
  NFIRAOS
  env.ctrl
  lgsWfs
  nacqNhrwfs
  ndme
> icd-db --list assemblies
  env.ctrl
  lgsWfs
  nacqNhrwfs
  ndme
> icd-db --subsystem NFIRAOS -o NFIRAOS.pdf

```


Implementation
--------------

Each JSON file is stored in its own MongoDB collection.
Here is a listing of the collections present after running this ingest command:


```
> icd-db --ingest examples/NFIRAOS/
> mongo icds4
MongoDB shell version v3.6.8
connecting to: mongodb://127.0.0.1:27017/icds4
> show collections
AOESW.aosq.command
AOESW.aosq.command.v
AOESW.aosq.component
AOESW.aosq.component.v
AOESW.aosq.publish
AOESW.aosq.publish.v
AOESW.aosq.subscribe
AOESW.aosq.subscribe.v
AOESW.aosq.v
AOESW.psfr.command
AOESW.psfr.command.v
...
```

The code then looks for collections with names ending in .subsystem, .component, .publish, .subscribe, or .command.
Queries can be run on all collections.
When detailed information is needed, the JSON in a collection is parsed into the same model classes used to
create the PDF document. Creating documents from the database works in the same way as creating them from files.
The JSON is parsed into model classes and then the document is generated from the model.

# API versions

The collection names without ".v" above are for the *current, unpublished versions" of the model files.
This is where the data is stored after ingesting the files into the database.

When ingesting API releases that were published on GitHub (using [icd-git](../icd-git)), the different versions of the
model files are *published* locally in the MongoDB and stored in collections that end with ".v".
