# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

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
  a single component oof NFIRAOS and another one between two components in NFIRAOS 
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


