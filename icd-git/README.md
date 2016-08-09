ICD GitHub Support
==================

icd-git is a command line application that reads release tags from the GitHub 
[ICD-Model-Files](https://github.com/tmtsoftware/ICD-Model-Files.git) repository and
allows you to publish ICD versions.

An ICD version is defined by an entry in a JSON formatted file named `icd-$subsytem1-$subsystem2.conf`
which is stored in the icds subdirectory of the repository. 
The file lists the two subsystems that make up the ICD, the ICD version, the versions of the two subsystems along with the
user name of the user that created the version, a comment and the date.

Example ICD Version Info File
-----------------------------

For example, an ICD file for the subsystems TEST and TEST2 looks like this (The `versions` field lists the
versions of the two subsystems). The file name in this case would be icds/icd-TEST-TEST2.conf:

```
subsystems = [ TEST, TEST2 ]

icds = [
  {
    icdVersion = 1.0
    versions = [ 1.0, 1.0 ]
    user = "user1"
    comment = "Created initial version..."
    date = "2016-07-20T14:13:58.008"
  }
  {
    icdVersion = 1.1
    versions = [ 1.1, 1.0 ]
    user = "user1"
    comment = "Updated model files..."
    date = "2016-08-04T15:23:19.002"
  }
  {
    icdVersion = 2.0
    versions = [ 2.0, 1.0 ]
    user = "user1"
    comment = "Updated model files again..."
    date = "2016-08-05T16:23:19.002"
  }
]
```

This file should only be created and edited via the icd-git application.

Subsystem Order
---------------

Note that since the ICD from A to B is equivalent to the one from B to A, the convention is
that the subsystems are listed in alphabetical order. This is enforced internally by the
icd-git application. Although the command line options include `--subsystem` and `--target`,
internally the options will be sorted so that subsystem comes before target in the sorting order.

Usage:
------

In most cases you need to specify the two subsystems using the --subsystem and --target options.
When *publishing* ICDs, you can add the subsystem versions after a colon ":". For example:
    
    --subsystem TEST:1.2 --target TEST2:1.0

Add the `-i` or `--interactive` option to enter the required options interactively, choosing from
a list of possible subsystems and versions.

```
icd-git 0.9
Usage: icd-git [options]

  -l | --list
        Prints the list of ICDs defined for the given subsystem and target subsystem options
  -s <subsystem>[:version] | --subsystem <subsystem>[:version]
        Specifies the subsystem (and optional version) to be used by the other options
  -t <subsystem>[:version] | --target <subsystem>[:version]
        Specifies the target or second subsystem (and optional version) to be used by the other options
  --icdversion <icd-version>
        Specifies the ICD version for the --unpublish option
  --versions
        Prints a list of available versions for the subsystems given by the subsystem and/or target options
  -i | --interactive
        Interactive mode: Asks to choose missing options
  --publish
        Publish an ICD based on the selected subsystem and target (Use together with --subsystem, --target and --comment)
  --unpublish
        Deletes the entry for the given ICD version (Use together with --subsystem, --target and --icdversion)
  --major
        Use with --publish to increment the major version
  -u <user> | --user <user>
        Use with --publish to set the GitHub user name (default: $USER)
  -p <password> | --password <password>
        Use with --publish to set the user's GitHub password
  -m <text> | --comment <text>
        Use with --publish to add a comment describing the changes made
  --help

  --version

```

Example Command Line Usage
--------------------------

List the ICD versions between the subsystems TEST and TEST2, in interactive mode:

```
$ icd-git -i --list
Please enter the first subsystem: (one of M2S, ROAD, OSS, TINC, SCMS, CSW, ESEN, SUM, REFR, NFIRAOS, LGSF, AOESW, TEST, STR, TEST2, HQ, APS, M1CS, IRMS, HNDL, ENC, M3S, WFOS, COOL, CRYO, COAT, ESW, SOSS, DPS, MCS, M1S, CLN, TCS, NSCU, DMS, TINS, IRIS, CIS)
TEST
Please enter the second subsystem: (one of M2S, ROAD, OSS, TINC, SCMS, CSW, ESEN, SUM, REFR, NFIRAOS, LGSF, AOESW, TEST, STR, TEST2, HQ, APS, M1CS, IRMS, HNDL, ENC, M3S, WFOS, COOL, CRYO, COAT, ESW, SOSS, DPS, MCS, M1S, CLN, TCS, NSCU, DMS, TINS, IRIS, CIS)
TEST2
- ICD Version 1.0 between TEST-1.0 and TEST2-1.0: published by $USER on 2016-08-09T14:50:59.024+02:00: My new version comment
```

Without the -i option, you need to specify all the required options:

```
$ icd-git --list --subsystem TEST --target TEST2
- ICD Version 1.0 between TEST-1.0 and TEST2-1.0: published by $USER on 2016-08-09T14:50:59.024+02:00: My new version comment
```

For publishing, you need to also add user name and password for git:

```
$ icd-git -i --publish
Please enter the first subsystem: (one of M2S, ROAD, OSS, TINC, SCMS, CSW, ESEN, SUM, REFR, NFIRAOS, LGSF, AOESW, TEST, STR, TEST2, HQ, APS, M1CS, IRMS, HNDL, ENC, M3S, WFOS, COOL, CRYO, COAT, ESW, SOSS, DPS, MCS, M1S, CLN, TCS, NSCU, DMS, TINS, IRIS, CIS)
TEST
Please enter the version for TEST: (one of List(1.0, 1.1, 1.2))
1.1
Please enter the second subsystem: (one of M2S, ROAD, OSS, TINC, SCMS, CSW, ESEN, SUM, REFR, NFIRAOS, LGSF, AOESW, TEST, STR, TEST2, HQ, APS, M1CS, IRMS, HNDL, ENC, M3S, WFOS, COOL, CRYO, COAT, ESW, SOSS, DPS, MCS, M1S, CLN, TCS, NSCU, DMS, TINS, IRIS, CIS)
TEST2
Enter the user name for Git: [$USER]

Enter the password for Git:

Enter a comment for the new ICD version:
This is a test comment
Created ICD version 1.1 based on TEST-1.1 and TEST2-1.0
```

Without the -i option, you need to specify all the required options:

```
$ icd-git --publish --subsystem TEST:1.2 --target TEST2:1.0 --user $USER --password XXXXX --comment "Another update"
Created ICD version 1.2 based on TEST-1.2 and TEST2-1.0
```