ICD Database
============

This project provides the ICD database interface and two command line applications, based on MongoDB.
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
in the [examples/3.0](../examples/3.0) directory.

```
icd-db 2.2.2
Usage: icd-db [options]

  --db <name>              The name of the database to use (default: icds4)
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
  -o, --out <outputFile>   Saves the selected API (or ICD) to the given file in a format based on the file's suffix (html, pdf) or generates code for the given API in a language based on the suffix ('scala', 'java', 'ts' (typescript), py (python))
  --drop [db|subsystem|component]
                           Drops the specified component, subsystem, or the entire icd database (requires restart of icd web app)
  --versions <subsystem>   List the version history of the given subsystem
  --diff <subsystem>:<version1>[,version2]
                           For the given subsystem, list the differences between <version1> and <version2> (or the current version)
  -m, --missing <outputFile>
                           Generates a 'Missing Items' report to the given file (dir for csv) in a format based on the file's suffix (html, pdf, otherwise text/csv formatted files are generated in given dir)
  -a, --archived <outputFile>
                           Generates an 'Archived Items' report for all subsystems (or the given one) to the given file in a format based on the file's suffix (html, pdf, csv)
  --allSubsystems          Include all subsystems in searches for publishers, subscribers, etc. while generating API doc (Default: only consider the one subsystem)
  --clientApi              Include subscribed events and sent commands in the API dic (Default: only include published events and received commands)
  --orientation [portrait|landscape]
                           For PDF output: The page orientation (default: landscape)
  --fontSize <size>        For PDF or HTML file output: The base font size in px for body text (default: 10)
  --lineHeight <height>    For PDF or HTML file output: The line height (default: 1.6)
  --paperSize [Letter|Legal|A4|A3]
                           For PDF output: The paper size (default: Letter)
  --documentNumber text    For PDF output: An optional document number to display after the title/subtitle
  --package package.name   Package name for generated Scala files (default: no package)
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

You can also generate code based on the contents of the icd database.
For example:

```
icd-db -s TCS --package tcs.api -o TcsApi.scala
icd-db -s TCS -c MCSAssembly --package tcs.mcsAssembly.api -o TcsMcsAssemblyApi.scala
```

The generated Scala file contains definitions for all of the event, command and parameter keys.
The first command generates keys for all TCS components. The second one only for the MCS Assembly.


icd-fits Command
--------------

The icd-fits command supports working with the FITS Dictionary:

```
icd-fits 3.0.0
Usage: icd-fits [options]

  -d, --db <name>          The name of the database to use (for the --ingest option, default: icds4)
  --host <hostname>        The host name where the database is running (for the --ingest option, default: localhost)
  --port <number>          The port number to use for the database (for the --ingest option, default: 27017)
  -c, --component <name>   Specifies the component to be used by any following options (subsystem must also be specified)
  -s, --subsystem <subsystem>[:version]
                           Specifies the subsystem (and optional version) to be used by any following options
  -t, --tag <tag>          Filters the list of FITS keywords to those with the given tag
  -l, --list               Prints the list of known FITS keywords
  --validate <file>        Validates a JSON formatted file containing the FITS Keyword dictionary and prints out any errors
  -g, --generate <file>    Generates an updated FITS dictionary JSON file by merging the one currently in the icd database with the FITS keyword information defined for event parameters in the publish model files. If a subsystem is specified (with optional version), the merging is limited to that subsystem.
  -i, --ingest <file>      Ingest a JSON formatted file containing a FITS Keyword dictionary into the icd database
  --ingestTags <file>      Ingest a JSON or HOCON formatted file defining tags for the FITS dictionary into the icd database
  --ingestChannels <file>  Ingest a JSON or HOCON formatted file defining the available FITS channels for each subsystem into the icd database
  -o, --out <outputFile>   Generates a document containing a table of FITS keyword information in a format based on the file's suffix (html, pdf, json, csv, conf (HOCON))
  --orientation [portrait|landscape]
                           For PDF output: The page orientation (default: landscape)
  --fontSize <size>        For PDF or HTML file output: The base font size in px for body text (default: 10)
  --lineHeight <height>    For PDF or HTML file output: The line height (default: 1.6)
  --paperSize [Letter|Legal|A4|A3]
                           For PDF output: The paper size (default: Letter)
  --help
  --version
```

The icd-viz command
-------------------

icd-viz is a command line app that uses Graphviz/Dot to generate graphs of subsystem component relationships based on command line arguments.

__Note__: The [Graphviz](https://graphviz.org/download/) apps needs to be installed on the local machine in order to use this application.

This app is based on previous [work in Python](https://raw.githubusercontent.com/tmtsoftware/NIC/master/script/icdRelationships.py) by Ed Chapin.

See also the [Graphviz](https://graphviz.org/doc/info/attrs.html) documentation for an explanation of options, such as *layout* or *overlap*.

## Usage

```
icd-viz 2.2.2
Usage: icd-viz [options]

  -d, --db <name>          The name of the database to use (default: icds4)
  -h, --host <hostname>    The host name where the database is running (default: localhost)
  -p, --port <number>      The port number to use for the database (default: 27017)
  --components prefix1[:version],prefix2[:version],...
                           Comma-separated list of primary component prefixes with optional versions (:version)
  --subsystems subsystem1[:version],subsystem2[:version],...
                           Comma-separated list of primary subsystems with optional versions (:version)
  --showplot <value>       Display plot in a window (default=true)
  -o, --imagefile <file>   Write image to file in format based on file suffix (default=None, formats: PDF, PNG, SVG, EPS)
  --dotfile <file>         Write dot source to file (default=None)
  --ratio <ratio>          Image aspect ratio (y/x) (default=0.5)
  --missingevents <value>  Plot missing events (default=true)
  --missingcommands <value>
                           Plot missing commands (default=false)
  --commandlabels <value>  Plot command labels (default=false)
  --eventlabels <value>    Plot event labels (default=true)
  --groupsubsystems <value>
                           Group components from same subsystem together (default=true)
  --layout one of: dot, fdp, sfdp, twopi, neato, circo, patchwork
                           Dot layout engine (default=dot)
  --overlap one of true, false, scale
                           Node overlap handling (default=scale)
  --splines <value>        Use splines for edges? (default=true)
  --omittypes <value>      Comma-separated list of component types (None, HCD, Assembly, Sequencer, Application) to omit as primaries (default='None')
  --imageformat <value>    Image format (Used only if imageFile not given or has invalid suffix). One of {PDF, PNG, SVG, EPS} (default='PDF')
  --help
  --version
```

All components specified through the `--components` or `--subsystems` arguments are considered *primary* nodes and indicated with solid ovals.
Any other component that they communicate with is a *secondary* node, and will be shown with a dashed oval.

The default colors used for the graph can be found in [reference.conf](src/main/resources/reference.conf) and can also be overridden with a command line option like `-Dicd.viz.color.IRIS=darkgreen` (replace IRIS with the subsystem):

* __commands__ - chocolate
* __events__ - dimgrey
* *missing commands or events* - red

__Subsystems__:
- NFIRAOS - green4
- AOESW - springgreen
- TCS - purple
- IRIS - blue
- APS - darkgreen
- OSS - darkred
- ESW - darkslategray
- *others* - grey

Examples:

* Plot all interfaces for a particular component to the screen,
  label events and commands, and show missing events and commands

```
icd-viz --components iris.oiwfs.poa --missingcommands true --missingevents true --commandlabels true --eventlabels true
```

![Graph](example.png)

* Plot all interfaces for two components only to a file called graph.pdf

```
icd-viz --components iris.oiwfs.poa,iris.rotator --imagefile graph.pdf
```

* Plot all interfaces for multiple subsystems and one component from another subsystem to screen, with no missing events shown

```
icd-viz --components iris.rotator --subsystems nfiraos,tcs --missingevents false
```

* Use "neato" layout to get a more readable graph with a lot of nodes

```
icd-viz --subsystems iris --layout neato --eventlabels false --overlap false

```

![Legend](legend.png)

Implementation
--------------

Each model file is stored in its own MongoDB collection.
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

The code then looks for collections with names ending in .subsystem, .component, .publish, .subscribe, .command, or .service.
Queries can be run on all collections.

# API versions

The collection names without ".v" above are for the *current, unpublished versions" of the model files.
This is where the data is stored after ingesting the files into the database.

When ingesting API releases that were published on GitHub (using [icd-git](../icd-git)), the different versions of the
model files are *published* locally in the MongoDB and stored in collections that end with ".v".
