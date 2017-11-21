# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

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
  
## [ICD v0.11] - 2016-02-22

### Changed

- Bug fixes and changes that were suggested in the last review.
  
- Incompatible changes have been made, so existing Mongodb databases should be deleted
  (for example with the command: icd-db --drop db) and any existing ICD files should be validated against the new schema and reimported. 
  You can do this using the web app. See the README.md files in the source code for more information.


