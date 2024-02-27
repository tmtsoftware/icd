# Change Log

All notable changes to this project will be documented in this file.
See also [JsonSchemaChanges.md](JsonSchemaChanges.md) for a list of changes in the JSON Schema for API model files.

## [ICD v3.0] - 2024-02-28

- Added "subsystem" constant in generated source files with subsystem name and version: For example: subsystem = "APS-1.4" (See Generate menu item in icd web app).
- Added file [MacOs-Max-Files-Limit.md](MacOs-Max-Files-Limit.md) describing how to increase the max-files limit on recent MacOS versions (This is required in order to run MongoDB when running the ICD web server locally, since the default setting is too low).
- Include FITS keyword table in PDFs for APIs (was previously only displayed in web app)
- Added a new toolbar item: "Missing" that generates a PDF containing a list of subscribed events with no publisher, sent commands with no receiver, and referenced components that are not defined. The table refers to the selected subsystem(s) or all subsystems, if none were selected (in the Select tab).
- Added ability to specify FITS keywords in publish model event parameter descriptions. The keywords (and optional channels) must already be in the FITS dictionary.
- Added a command line option to icd-fits to generate a FITS dictionary JSON file by merging the one currently in the icd database with the FITS keywords defined for the event parameters in specified subsystems. This can be used to update the FITS dictionary to match the entries in the model files for a given subsystem (or all subsystems).
- Added "Alarms" toolbar item, to generate a PDF listing the alarms for the selected subsystem/component or all subsystems.
- Changed the web and PDF displays to include information about alarms in all ICDs with ESW.
- For graphs, added the option to display only subsystems (instead of the subsystem components). Added a new command line option for this to the icd-viz app and a new checkbox in the web app's graph options popup. 
- For graphs: The graph generated for ICDs (two subsystems/components) now only includes connections between the two (previously connections to other subsystems were also included).
- Added new optional "category" field for events with possible values: [DEMAND, CONTROL, EVENT, STATUS] (See "Event Categories" at end of [README.md](README.md))
- Replaced general usage of "TMT" with "TIO" (TMT International Observatory) in web app and docs.
- Added validation check for parameters that the "defaultValue" is valid for the declared type
- Set a fixed with for inline OpenApi display frames
- Added code to revert database if post-ingest validation errors are found.

## [ICD v2.2.3] - 2023-03-05

- Changed the format of the FITS-Tags.conf file to allow one tag to inherit keywords from another. For example, the IRIS and MODHIS tags now "inherit" the keywords from the "DL" (diffraction-limited) tag, so these are automatically displayed when the IRIS or MODHIS tags are selected in the web app.

## [ICD v2.2.2] - 2022-11-18

- Fixed scrollbar display in web app (was too thin or not visible)
- Fixed issue generating PDFs with the option selected: "Include only the details that are expanded in the HTML view"
- Display subscriber info in summary table if option "Include client API information" is selected, but not for ICDs
- Updated dependencies, code cleanup
- Added additional validation checks for duplicate or incorrect component, event or command names when manually
  ingesting subsystem model files
- Incremented major version to 3.0.0

## [ICD v2.2.1] - 2022-11-11

- Fixed an issue where published images were not included in the generated documentation if no events were published.
- Added a validation check for conflicting component names when manually ingesting or uploading model files into the icd
  database. Also added a check for cases found in already published APIs.
- Minor performance improvements
- Added details table for published images to generated PDFs and changed the format used for the web version
- Added --documentNumber option to icd-db (and text field to web app's PDF options popup). If given, the text is
  displayed under the subtitle in the generated PDF

## [ICD v2.2.0] - 2022-11-01

- Added options to icd-db command line app to generate code containing the event, command and parameter keys. (currently
  Scala, Java, Typescript and Python are supported)
- Added a new modelVersion "3.0", which defines the allowed parameter "units" matching the ones defined in the CSW
  framework and adds the parameter types "taiTime" and "utcTime" (same as taiDate and utcDate, which are still allowed:
  taiTime and utcTime should be preferred, matching the CSW names).
- Added [jsonnet](https://jsonnet.org/) support. Model files with the '.jsonnet' suffix are processed with jsonnet
  before storing in the icd database. This can be used to avoid repetition in the model files.
  See [this example](examples/3.0/TEST/jsonnet-example/publish-model.jsonnet).
- Changed the publish model to only allow [predefined Observe Events](icd-db/src/main/resources/3.0/observe-events.conf)
- Added support for documenting TMT CSW components that are HTTP web services
  using [OpenAPI](https://swagger.io/specification/).
- Added a new, optional icd model file: service-model.conf, that contains information about the HTTP services that a
  component provides or requires. For each service provided, the model file also contains the name of the OpenAPI JSON
  file that describes the service.
- Updated dependencies
- Replaced deprecated ScalaJS Ajax usage with recommended dom fetch API
- Removed RaDec type from JSON schema for parameters in v2.0 and later model files
- Removed support for "struct" parameter type, since it was removed from CSW
- Fixed display of "default" parameter values
- Improved "busy" cursor display
- Removed support for uploading a zip file containing the icd model files, since modern browsers now all support
  uploading a directory
- Removed dependency on "less" and converted/renamed icd.less to icd.css
- Updated list of allowed CSW Units
- Updated all dependencies to the latest versions, including Bootstrap-5.2.0 and support for MongoDB-6.0
- Added support for documenting published and subscribed images (TMT VIZ)
- Added support for a FITS keyword dictionary, and the icd-fits command line app, FITS Dictionary tab (FITS Keywords
  will be automatically imported once the DMS subsystem model files have been published. For now, you can
  use `icd-fits -i examples/3.0/FITS-Dictionary.json --ingestTags examples/3.0/FITS-Tags.conf`) or manually
  upload/ingest the [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files) GitHub repo in the icd web app.
- Updated to Play-Framework-2.9.0-M2, which means icd can now be compiled and run with Java-17 (as well as Java-11).
- Fixed issues in the handling of older subsystem versions when generating graphs
- Fixed issues dealing with "refs"
- Improved the title and description display for inner-subsystem ICDs (changed to avoid duplicating information, use
  component description where applicable).

## [ICD v2.1.2] - 2021-05-25

- Added support for optional `${subsystem}-icd-model.conf` files (for example: `IRIS-icd-model.conf`
  , `TCS-icd-model.conf`) that add information about the ICD between `${subsystem}` and the subsystem being described.
  See [TEST2-icd-model.conf](examples/2.0/TEST/TEST2-icd-model.conf)
  or [TEST-icd-model.conf](examples/2.0/TEST2/TEST-icd-model.conf) for an example, or for more realistic
  examples: [here](examples/2.0/TEST/IRIS-icd-model.conf) and [here](examples/2.0/TEST2/IRIS-icd-model.conf)

## [ICD v2.1.1] - 2021-05-01

### Changed

- Fixed a minor issue that could occur in the web app when the uploaded model file versions in subsystem-model.conf and
  component-model.conf were different.

- Changed size calculations for archived events: Use 12 hours/day (was 24), list hourly data sizes, changed event
  overheads to match CSW versions.

- Added "Command: " to command description heading to match event headings.

- Fixed an issue that could cause a newly published ICD version to not be automatically ingested into the local icd
  database in certain circumstances

## [ICD v2.1.0] - 2020-10-12

### Added

- Added a new component model file: alarm-model.conf, which replaces the "alarms" section in publish-model.conf
  (The old version is still supported for backward compatibility).
  See [DEOPSICDDB-98](https://tmt-project.atlassian.net/secure/RapidBoard.jspa?rapidView=97&projectKey=DEOPSICDDB&modal=detail&selectedIssue=DEOPSICDDB-98)
  .

- Added support for specifying an optional minLength and maxLength for string parameter types (to help in calculating
  the archived size for events)

### Changed

- Changed the "attributes" keyword for events (in publish-model.conf) and the "args" keyword for commands
  (in command-model.conf) to "parameters", to match the CSW terms.
  The previous names are still accepted for backward compatibility.
  Also renamed the json-schema files used internally for validation.
  See [DEOPSICDDB-113](https://tmt-project.atlassian.net/secure/RapidBoard.jspa?rapidView=97&projectKey=DEOPSICDDB&modal=detail&selectedIssue=DEOPSICDDB-113)
  .
  The generated APIs and ICDs now also display "Parameters" instead of "Attributes", etc.

## [ICD v2.0.0] - 2020-09-09

### Added

- Added "master" as an API version that is updated automatically from GitHub on startup or refresh of the web app.

- Added support for LaTeX math formulas in description texts, delimited by $\`...\`$ for inline or \`\`\`math...\`\`\`
  for a block.
  See [examples/2.0/TEST/envCtrl/publish-model.conf](examples/2.0/TEST/envCtrl/publish-model.conf) and
  [examples/2.0/TEST/envCtrl/command-model.conf](examples/2.0/TEST/envCtrl/command-model.conf) for examples.
  The LaTeX string is converted to an image and then inserted in the HTML or PDF.

- Added support for UML (and Graphviz/Dot) markup in description texts,
  based on [PlantUML](https://plantuml.com/), delimited by \`\`\`uml...\`\`\`.
  See [examples/2.0/TEST/envCtrl/component-model.conf](examples/2.0/TEST/envCtrl/component-model.conf).
  __Note__: The [Graphviz](https://graphviz.org/download/) apps needs to be installed on the server (or local machine,
  for the command line) in order to use this feature.

- Added icd-viz command line app, based on Graphviz/Dot and Ed Chapin's `icdRelationships.py`,
  that generates a graph of component relationships.

- Added a "Graph" toolbar item to the icd web app that displays a graph of relationships of the selected subsystems or
  components.

- Added icd-db command line option "--clientApi" and a checkbox in the web app to include subscribed events and sent
  commands in an API document (the default was changed to only include published events or alarms and received commands)
  .

- Added new option to icd-git command: `--ingestMissing`: Ingests any APIs or ICDs that were published, but are not yet
  in the local database, plus any master branch versions.

- Added "ref" keyword for events, commands, attributes to enable reuse of all or part of another item in the model
  files.
  See "Reusing Event, Command and Attribute Definitions" at the end of [README.md](README.md) for more details.

### Changed

- Changed API layout to include only published events or alarms and received commands by default (with option to include
  subscribed events and sent commands as well, like before).

- The checkboxes in the web app select dialog are only enabled when a single subsystem is selected (since they only
  apply to APIs).

- Changed the JSON schema for attributes to allow "inf" as the value for "maximum" or "exclusiveMaximum" and "-inf" as
  the value for "minimum" or "exclusiveMinimum".

- Changed the JSON schema for attributes to include the "allowNaN" keyword. Set to true if NaN values are allowed.
  Default value is false.

## [ICD v1.3.1] - 2020-06-08

### Added

- Added icd-db command line option to increase font size for PDFs.

### Changed

- Changed PDF and Archive buttons to display a popup with options for orientation and font size.

## [ICD v1.3.0] - 2020-06-04

### Changed

- Fixed a bug that requires rebuilding the icd database. This is done automatically when you run icdwebserver,
  or you can run `icd-git --ingest` to do it manually.

- Added the following subsystems back to the allowed list for the 2.0 model file schema: ENC, STR, M2S, M3S, TINS
  and created the related repos under https://github.com/tmt-icd/.

## [ICD v1.2.1] - 2020-06-01

### Added

- Added error message for duplicate component name in different directories

- Added simple auth/login dialog for use on public servers (Use -Dicd.isPublicServer=true)

- Added code to automatically tag API GitHub repos when an API is published (For example: "v1.2"),
  to make it easier to compare API versions

### Changed

- Removed alarms from the "Missing Items" report generated by icd-db
- Alarm changes: Components can no longer subscribe to alarms
- Alarm property name changes (only for schema-2.0): "acknowledge" changed to "autoAck" (Opposite meaning)

__Note: If you are using modelVersion 2.0, please update and run: `icd-git --ingest` to rebuild the local icd
database.__

- Fixed some issues with browser history after clicking on HTML links in the icd web app

- Removed `-Dicd.allowUpload=false` option, replaced with `-Dicd.isPublicServer=true`, which controls
  the features displayed when running the icd web server on a public server.

- Improved warning message displayed in HTML/PDF output for missing components

## [ICD v1.1.2] - 2020-05-14

### Added

- Added an optional "role" field for received commands. The allowed values are "eng", "admin" or "user" (default: "
  user").
  Sending a command to a component will require the correct user role for the subsystem in the CSW AAS auth service.

### Changed

- Minor CSS changes to support different screen sizes

- Updated archive size for taiDate type to 12 bytes (was 16)

- Updated icd-web-server.sh script (used to run on public server)

- Removed unneeded subsystems

## [ICD v1.1.1] - 2020-04-20

### Added

- Added option to generate PDFs in portrait or landscape orientation
  (Landscape orientation is preferred, so that there is enough room in the many tables in the PDF output).

- Updated itextpdf dependency version to itext-7

- PDFs for published APIs and ICDs are now cached (Only for full, published APIs and ICDs without references
  to external subsystems - i.e.: The "Search all Subsystems" option is not checked).

## [ICD v1.1.0] - 2020-03-21

### Changed

- For modelVersion = "2.0": Removed the "prefix" setting from component-model.conf,
  since after changes in CSW, prefix is always just $subsystem.$component
  (Component names can contain dots and spaces, except leading or trailing)

- For modelVersion = "2.0": Component names can no longer contain a dash "-" (to match changes in CSW)

- Fixed an issue where the version of the second subsystem in an ICD was ignored

- Fixed an issue where after renaming a component, the old name could show up

## [ICD v1.0.2] - 2020-03-06

### Changed

- Fixed issue where subscribed telemetry and eventStreams were being ignored
  (Now they are automatically converted to events, since telemetry and eventStreams have been removed in the new JSON
  schema)

## [ICD v1.0.1] - 2020-03-03

### Changed

- Made changes to allow inner document links in MarkDown descriptions (DEOPSICDDB-54)

- Changed link targets to ensure unique names (in case two events in different components have the same name)

- Fixed bug in web app dealing with "Search all Subsystems" option

- Changes to allow embedded dots in component names

- Updated dependencies to fix issue with PDF generation (DEOPSICDDB-93)

## [ICD v1.0.0] - 2020-02-21

### Added

- Added a new overview/status dialog that displays a table of the latest published APIs and ICDs for a selected
  subsystem.

- Added Unpublish button to publish dialog (Use in case you published something by mistake).

- Added confirmation popup for publishing or unpublishing an API or ICD.

- Added an "Archive" item to the webapp that displays a PDF detailing the sizes of all archived items (events, etc.) in
  the selected subsystem and component (default: all subsystems).

- Added the icd software version to the title in the Status page.

### Changed

- The Publish dialog access is restricted to those with write access to the
  [ICD-Model-Files](https://github.com/tmt-icd/ICD-Model-Files)  repository
  and is only enabled when starting icdwebserver with the `-Dicd.isPublicServer=true`
  option.

- Fixed issue that could cause PDF generation to fail if embedded HTML in description
  text was not valid XHTML.

## [ICD v0.17] - 2019-12-06

### Added

- Added *total event size* and *yearly accumulation* to the display for archived events (also added to the Archived
  Items report produced by icd-db).
  Note that for some types, such as strings, the sizes are only guesses, since the string length is not known ahead of
  time.
  If `maxRate` is zero or not defined, 1 Hz is assumed.
  Note also that the actual space required to archive events may be much less, due to the storage format (CBOR),
  compression, etc.

### Removed

- Removed `minRate` and `archiveRate` from the 2.0 JSON Schema for APIs. From now on, only `maxRate` should be used,
  which is the maximum publish rate for the event in Hz. This is used to calculate the *yearly accumulation* or size of
  the data for archived events for a year.

## [ICD v0.16] - 2019-11-18

### Added

- Added a new Publish dialog to the icd web app

- The `Upload` feature in the icd web app. which allows you to ingest local model files into the ICD database,
  can now be disabled by changing the `icd.isPublicServer` configuration setting
  in icd-web-server/application.conf or by starting the web app like this: `icdwebserver -Dicd.isPublicServer=true`.
  The Upload feature should be disabled when `icdwebserver` is running in a public network and should only
  display the actual API and ICD releases, which are stored on GitHub.

- Updated the list of allowed subsystem names for ICD model files
  (The old list is only allowed when modelVersion is set to "1.0". If it is set to "2.0", the new list is used).

## [ICD v0.15] - 2019-11-06

### Changed

- Bug fixes

### Added

- Added support for new types for attributes. The list of available types matches those implemented in CSW parameter
  sets:
  (*array, struct, boolean, integer, number, string, byte, short, long, float, double, taiDate, utcDate, raDec, eqCoord,
  solarSystemCoord, minorPlanetCoord, cometCoord, altAzCoord, coord*)

## [ICD v0.14] - 2019-10-29

### Changed

- Changed the default JSON schema for model files. The old 1.0 schema is still supported. You can use the new model file
  formats by setting modelVersion="2.0" in component-model.conf and subsystem-model.conf.

- The old and new JSON schema descriptions are now under [icd-db/src/main/resources](icd-db/src/main/resources) in the
  [1.0](icd-db/src/main/resources/1.0) and [2.0](icd-db/src/main/resources/2.0) directories.
  Examples of the old and new formats can be found in the [examples](examples) directory.

- With the new JSON schema for attributes, the values for *exclusiveMinimum* and *exclusiveMaximum* are the numerical
  values
  (previously it was a boolean value).

- Changed the icd web app (*icdwebserver*) to support *component to component ICDs* as well as viewing selected
  components in an API.

- Changed the icd web app to automatically ingest published APIs and subsystems from GitHub if missing on the first
  start
  (previously they were ingested only as needed, which caused delays and other issues)

- Bug fixes

### Removed

- Removed *telemetry* events and *eventStreams* from the publish model
  (These are automatically converted to *events* when imported from modelVersion 1.0 files).

- Removed *archive* field from alarm model.

### Added

- Added *observeEvents* and *currentStates* as an event types for the publish model.

- Added more required fields to the alarm model:
  *location, alarmType, probableCause, operatorResponse, autoAck, latched.*

- Added support for *struct* types for attributes.
  See [examples/2.0/TEST/envCtrl/publish-model.conf](examples/2.0/TEST/envCtrl/publish-model.conf) for some sample
  struct declarations.

## [ICD v0.12] - 2019-08-15

### Added

- New `icd-git` command line tool and features in the icd web app.
  Now published versions of subsystem APIs and ICDs are managed with GitHub repositories and JSON files (
  See [README.md](README.md)).

- Now warnings are displayed for subscribed items where there is no publisher, or sent commands/configurations, where no
  receiving end was defined.

- New `--target-component` option to the [icd-db](icd-db) command line app:
  Can be used together with the `--component` option to create a PDF for an ICD between two components in different
  subsystems,
  or just to restrict the document to items related to the target component.

- Added two new icd-db options: --archive (-a) to generate a report of all events that have "archive" set to true,
  and --missing (-m) to generate a report listing published events with no subscribers, subscribed events with no
  publishers, referenced components with no definition, etc.

- Added a new primitive type "taiDate" that can be used in ICD model files to indicate a TAI date or time type.

### Changed

- Removed the *Publish* feature from the icd web app.
  Publishing is now done by a TMT admin using the `icd-git` command line tool.

- Changed the layout for APIs and ICDs to include a summary table at top with links to details below.

- Changed the ICD PDF layout to display the published items from both subsystems, with links to the subscribers
  (instead of showing a list of the subscribed items)

- Changed the way the PDF document titles are created, so that (for the command line
  at least) you can have component to component ICDs.
  For example, these two commands will generate an ICD PDF from a single component of IRIS to
  a single component of NFIRAOS and another one between two components in NFIRAOS
  (*Note that if the versions are not specified, you get the latest unpublished version*):

```$xslt
   icd-db -s IRIS:1.5 -t NFIRAOS:1.3 --component csro-env-assembly --target-component encl -o csro-env-assembly-encl.pdf

   icd-db -s NFIRAOS -t NFIRAOS --component dm --target-component rtc -o dm-rtc.pdf
```  

## [ICD v0.11] - 2016-02-22

### Changed

- Bug fixes and changes that were suggested in the last review.

- Incompatible changes have been made, so existing Mongodb databases should be deleted
  (for example with the command: icd-db --drop db) and any existing ICD files should be validated against the new schema
  and reimported.
  You can do this using the web app. See the README.md files in the source code for more information.


