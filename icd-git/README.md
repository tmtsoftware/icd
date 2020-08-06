ICD GitHub Support
==================

icd-git is a command line application that reads model files from the GitHub 
[ICD-Model-Files](https://github.com/tmt-icd/ICD-Model-Files.git) repositories and,
if you have admin permissions,
allows you to publish subsystem APIs and ICDs.

Note that publishing APIs and ICDs is now done via the icd web app by a TMT admin.

In order for a subsystem to be part of an ICD, the API for it first needs to be published (Note: You need commit access to the above repo for this to work):

    icd-git --subsystems TEST --publish --user $USER --password XXXXX --comment "Some comment"
    icd-git --subsystems TEST2 --publish --user $USER --password XXXXX --comment "Some comment"
    icd-git --subsystems TEST,TEST2 --publish --user $USER --password XXXXX --comment "Some comment"

(Replace $USER with your GitHub user name and add your GitHub password.)
The first two lines publish the TEST and TEST2 subsystems. The last line publishes the ICD between
TEST and TEST2. 

Publishing a subsystem API or ICD creates an entry in a JSON file in the 
[ICD-Model-Files](https://github.com/tmt-icd/ICD-Model-Files.git)/apis or 
[ICD-Model-Files](https://github.com/tmt-icd/ICD-Model-Files.git)/icds directory.
These files should not be manually edited. They are used to store version related information.

An ICD version is defined by an entry in a JSON formatted file named `icd-$subsytem1-$subsystem2.json`
which is stored in the icds subdirectory of the repository. 
The file lists the two subsystems that make up the ICD, the ICD version, the versions of the two subsystems along with the
user name of the user that created the version, a comment and the date.

The version files for the subsystem APIs also contain the git commit ids that correspond to that version.
These are used to ingest the version history into the ICD database.

Subsystem Order
---------------

Note that since the ICD from A to B is equivalent to the one from B to A, the convention is
that the subsystems are listed in alphabetical order. This is enforced internally by the
icd-git application. 

Usage:
------

In most cases you need to specify one or two subsystems using the --subsystems option 
(Subsystems are separated by commas, with no spaces).
For example:

    --subsystems TEST,TEST2
    
Where it makes sense, such as when *publishing* ICDs, you can add the subsystem versions after a colon ":". For example:
    
    --subsystems TEST:1.2,TEST2:1.0

Add the `-i` or `--interactive` option to enter the required options interactively, choosing from
a list of possible subsystems and versions.

```
Usage: icd-git [options]

  -l, --list               Prints the list of API or ICD versions defined on GitHub for the given subsystem options
  -s, --subsystems <subsystem1>[:version1],...
                           Specifies the subsystems (and optional versions) of the APIs to be used by the other options
  --icdversion <icd-version>
                           Specifies the ICD version for the --unpublish option
  -i, --interactive        Interactive mode: Asks to choose missing options
  --publish                Publish an API (one subsystem) or ICD (two subsystems) based on the options (--subsystems, --user, --password, --comment)
  --unpublish              Deletes the entry for the given API or ICD version (Use together with --subsystems, --icdversion)
  --major                  Use with --publish to increment the major version
  -u, --user <user>        Use with --publish or --unpublish to set the GitHub user name (default: $USER)
  -p, --password <password>
                           Use with --publish or --unpublish to set the user's GitHub password
  -m, --comment <text>     Use with --publish to add a comment describing the changes made
  -d, --db <name>          The name of the database to use (for the --ingest option, default: icds)
  --host <hostname>        The host name where the database is running (for the --ingest option, default: localhost)
  --port <number>          The port number to use for the database (for the --ingest option, default: 27017)
  --ingest                 Ingests the selected subsystem and target subsystem model files and ICDs from GitHub into the ICD database (Ingests all subsystems, if neither option is given)
  --help                   
  --version                
```

Additional Configuration Options for Testing
--------------------------------------------

For testing, you may want to use a repository other than the default (https://github.com/tmt-icd).
There are two system properties (Java -D options) that you can use to override the default Git repository:

* -Dcsw.services.icd.github.parent.uri=https://github.com/*yourRepo*

This overrides the base URI used for the subsystem Git repositories (The default value is https://github.com/tmt-icd).

* -Dcsw.services.icd.github.uri=https://github.com/*yourRepo*

This overrides only the base URI containing ICD-Model-Files repository, which contains version information stored in JSON files in 
the apis and icds subdirectories (The default value is also https://github.com/tmt-icd).
You could override this URI in order to test making releases without actually publishing them on the official Git repository.

Note that these options / system properties will work for both the icd-git and icdwebserver applications.

Example Command Line Usage
--------------------------

List the ICD versions between the subsystems TEST and TEST2, in interactive mode:

```
$ icd-git -i --list
Please enter the first subsystem: (one of M2S, ROAD, OSS, TINC, SCMS, CSW, ESEN, SUM, REFR, NFIRAOS, LGSF, AOESW, TEST, STR, TEST2, HQ, APS, M1CS, IRMS, HNDL, ENC, M3S, WFOS, COOL, CRYO, COAT, ESW, SOSS, DPS, MCS, M1S, CLN, TCS, NSCU, DMS, TINS, IRIS, CIS)
TEST
Please enter the second subsystem: (one of M2S, ROAD, OSS, TINC, SCMS, CSW, ESEN, SUM, REFR, NFIRAOS, LGSF, AOESW, TEST, STR, TEST2, HQ, APS, M1CS, IRMS, HNDL, ENC, M3S, WFOS, COOL, CRYO, COAT, ESW, SOSS, DPS, MCS, M1S, CLN, TCS, NSCU, DMS, TINS, IRIS, CIS)
TEST2

- ICD Version 1.0 between TEST-1.0 and TEST2-1.0: published by abrighton on 2016-08-17T23:38:39.098+02:00: Some comment

```

Without the -i option, you need to specify all the required options:

```
$ icd-git --list --subsystems TEST,TEST2
```

For publishing, you need to also add your GitHub user name and password:

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
$ icd-git --publish --subsystems TEST:1.2,TEST2:1.0 --user $USER --password XXXXX --comment "Another update"
Created ICD version 1.2 based on TEST-1.2 and TEST2-1.0
```

To ingest an ICD for two subsystems into the local ICD database, use a command like this:

    $ icd-git --ingest --subsystems TEST,TEST2

This deletes the current ICD database, 
loads the ICD model files (with version history) for the TEST and TEST2 subsystems into the ICD database, 
reads the ICD version information from GitHub and updates the database. After running this command,
you can use the icd web app to browse the ICD or use the `icd-db` command line app to generate a pdf
of the ICD.

If you leave off the `--subsystems` option, all subsystems and ICDs are ingested into the database:

    $ icd-git --ingest --subsystems TEST,TEST2

It is also possible to ingest only specific versions of subsystems:

    $ icd-git --ingest --subsystems TEST:1.2,TEST2:1.1

In that case, only ICDs that include those subsystem versions will be defined.

