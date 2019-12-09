# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

## [ICD v0.17] - 2019-12-06

### Added

- Added *total event size* and *yearly accumulation* to the display for archived events (also added to the Archived Items report produced by icd-db).
   Note that for some types, such as strings, the sizes are only guesses, since the string length is not known ahead of time. 
   If `maxRate` is zero or not defined, 1 Hz is assumed.
   Note also that the actual space required to archive events may be much less, due to the storage format (CBOR), compression, etc.

### Removed

- Removed `minRate` and `archiveRate` from the 2.0 JSON Schema for APIs. From now on, only `maxRate` should be used, which is the maximum publish rate for the event in Hz. This is used to calculate the *yearly accumulation* or size of the data for archived events for a year.

## [ICD v0.16] - 2019-11-18

### Added

- Added a new Publish dialog to the icd web app

- The `Upload` feature in the icd web app. which allows you to ingest local model files into the ICD database,
  can now be disabled by changing the `icd.allowUpload` configuration setting 
  in icd-web-server/application.conf or by starting the web app like this: `icdwebserver -Dicd.allowUpload=false`.
  The Upload feature should be disabled when `icdwebserver` is running in a public network and should only 
  display the actual API and ICD releases, which are stored on GitHub. 
  
- Updated the list of allowed subsystem names for ICD model files
  (The old list is only allowed when modelVersion is set to "1.0". If it is set to "2.0", the new list is used).

## [ICD v0.15] - 2019-11-06

### Changed

- Bug fixes

### Added

- Added support for new types for attributes. The list of available types matches those implemented in CSW parameter sets:
  (*array, struct, boolean, integer, number, string, byte, short, long, float, double, taiDate, utcDate, raDec, eqCoord, solarSystemCoord, minorPlanetCoord, cometCoord, altAzCoord, coord*)


## [ICD v0.14] - 2019-10-29

### Changed

- Changed the default JSON schema for model files. The old 1.0 schema is still supported. You can use the new model file formats by setting modelVersion="2.0" in component-model.conf and subsystem-model.conf. 

-  The old and new JSON schema descriptions are now under [icd-db/src/main/resources](icd-db/src/main/resources) in the 
 [1.0](icd-db/src/main/resources/1.0) and [2.0](icd-db/src/main/resources/2.0) directories. 
  Examples of the old and new formats can be found in the [examples](examples) directory. 
  
- With the new JSON schema for attributes, the values for *exclusiveMinimum* and *exclusiveMaximum* are the numerical values 
  (previously it was a boolean value). 

- Changed the icd web app (*icdwebserver*) to support *component to component ICDs* as well as viewing selected components in an API.

- Changed the icd web app to automatically ingest published APIs and subsystems from GitHub if missing on the first start
 (previously they were ingested only as needed, which caused delays and other issues)

- Bug fixes

### Removed

- Removed *telemetry* events and *eventStreams* from the publish model
  (These are automatically converted to *events* when imported from modelVersion 1.0 files).
  
- Removed *archive* field from alarm model.

### Added

- Added *observeEvents* and *currentStates* as an event types for the publish model.

- Added more required fields to the alarm model: 
    *location, alarmType, probableCause, operatorResponse, acknowledge, latched.*

- Added support for *struct* types for attributes.
  See [examples/2.0/TEST/envCtrl/publish-model.conf](examples/2.0/TEST/envCtrl/publish-model.conf) for some sample struct declarations.


## [ICD v0.12] - 2019-08-15

### Added

- New `icd-git` command line tool and features in the icd web app. 
  Now published versions of subsystem APIs and ICDs are managed with GitHub repositories and JSON files (See [README.md](README.md)).
  
- Now warnings are displayed for subscribed items where there is no publisher, or sent commands/configurations, where no receiving end was defined.

- New `--target-component` option to the [icd-db](icd-db) command line app: 
  Can be used together with the `--component` option to create a PDF for an ICD between two components in different subsystems,
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
  (for example with the command: icd-db --drop db) and any existing ICD files should be validated against the new schema and reimported. 
  You can do this using the web app. See the README.md files in the source code for more information.


