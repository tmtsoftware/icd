# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

### Added

- New `icd-git` command line tool and features in the icd web app. 
  Now published versions of subsystem APIs and ICDs are managed with GitHub repositories and JSON files (See [README.md](README.md)).
  
- Now warnings are displayed for subscribed items where there is no publisher, or sent commands/configurations, where no receiving end was defined.

### Changed

- Removed the *Publish* feature from the icd web app. 
  Publishing is now done by a TMT admin using the `icd-git` command line tool.
  
## [ICD v0.11] - 2016-02-22

### Changed

- Bug fixes and changes that were suggested in the last review.
  
- Incompatible changes have been made, so existing Mongodb databases should be deleted
  (for example with the command: icd-db --drop db) and any existing ICD files should be validated against the new schema and reimported. 
  You can do this using the web app. See the README.md files in the source code for more information.


