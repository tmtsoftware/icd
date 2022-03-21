# JSON Schema Changes for Model Version 3.0

To use the new model version, set "modelVersion" to "3.0" in component-model.conf and subsystem-model.conf.

* The value for the "units" field for parameters must be one of the allowed CSW unit types defined in [units.conf](icd-db/src/main/resources/3.0/units.conf).

* Removed "struct" and "raDec" types, added "taiTime" and "utcTime" types (same as "taiDate" and "utcDate", which are deprecated, but still supported).

# JSON Schema Changes for Model Version 2.0

The API model files subsystem-model.conf and component-model.conf contain a field: `modelVersion` that was previously 
set to "1.0" to indicate that the model files for that component or subsystem use that version of the JSON Schema 
for validation. The new vesion "2.0" contains some changes. Existing APIs that use model version "1.0" are still
backward compatible. To upgrade to the new version, set modelVersion to "2.0" and mkae the necessary changes
to the model files (See the "schema-2.0" branch in each API repo, where that has already been done).

## Changes to model version 2.0

* Removed "struct" type (Schema version was not changed, since no known uses of "struct" were found)

## Changes between modelVersion 1.0 and 2.0

### Alarms (publish-model.conf)

* Alarms have new required fields: location, alarmType, probableCause, operatorResponse, and two new optional field: autoAck and latched.

* The "severity" field has been renamed to "severityLevels", which is an array of [Warning, Major, Critical].

* The "archive" field has been removed.

### Events (publishModel.conf)

* Removed "telemetry", "eventStreams"

* Added "observeEvents", "currentState"

* Added attribute types: enum: [ array, struct, boolean, integer, string, byte, short, long, float, double, taiDate, utcDate, raDec, eqCoord, solarSystemCoord, minorPlanetCoord, cometCoord, altAzCoord, coord ] ("number" should not be used anymore).

* exclusiveMinimum and exclusiveMaximum are no longer boolean values: Now they are numerical (the exclusive min or max value).

### Commands (command-model.conf)

* Commands have new fields: preconditions, postconditions, requiredArgs, args, completionType, resultType, completionCondition, role 

### Components (component-model.conf)

* Component names may no longer contain a dash "-".

* The prefix field has been removed (Now the prefix is always $subsystem.$component).

### Subsystems

The list of subsystem APIs has changed: 
```
  SUM, // (SUM, Components: FMCS, HBS)
  CLN, // Mirror Cleaning System
  TCS, // Telescope Control System (Components K, CM, MCS, SHS, ENC, M2, M3, PFC, GMS...)
  M1CS, // M1 Control System
  APS, // Alignment and Phasing System
  OSS, // Observatory Safety System
  ESEN, // Engineering Sensor System
  NFIRAOS, // Narrow Field Infrared AO System
  NSCU, // NFIRAOS Science Calibration Unit
  LGSF, // Lasert Guide Star Facility
  AOESW, // AO Executive Software
  CRYO, // Cryogenic Cooling System
  IRIS, // InfraRed Imaging Spectrometer
  MODHIS, // Multi-Object Diffraction-limited High-resolution Infrared Spectrograph
  REFR, // Refrigeration Control System
  WFOS, // Wide Field Optical Spectrometer
  CIS, // Communications and Information Systems
  CSW, // Common Software
  DMS, // Data Management System
  ESW, // Executive Software System
  SOSS, // Science Operations Support System
  DPS, // Data Processing System
  SCMS, // Site Conditions Monitoring System
```
