# Using the icd-fits command line app

The `icd-fits` command line application can be used to view and update the FITS Dictionary and related files or to generate a PDF or other type of file displaying the FITS keywords for a given subsystem or component.

In normal operations, the FITS Dictionary is loaded automatically from the published `DMS-Model-Files` GitHub repository. At the time of writing this has not yet been published.

Below are the available options for the `icd-fits` program, which you can list with the `icd-fits --help` option:

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
  -g, --generate <file>    Generates an updated FITS dictionary JSON file by merging the one currently in the
                        icd database with the FITS keyword information defined for event parameters in the
 				    publish model files. If a subsystem is specified (with optional version), the
 				    merging is limited to that subsystem.
  -i, --ingest <file>      Ingest a JSON formatted file containing a FITS Keyword dictionary into the icd database
  --ingestTags <file>      Ingest a JSON or HOCON formatted file defining tags for the FITS dictionary into the icd database
  --ingestChannels <file>  Ingest a JSON or HOCON formatted file defining the available FITS channels for each subsystem into the icd database
  -o, --out <outputFile>   Generates a document containing a table of FITS keyword information in a format 
			   based on the file's suffix (html, pdf, json, csv, conf (HOCON))
  --orientation [portrait|landscape]
                           For PDF output: The page orientation (default: landscape)
  --fontSize <size>        For PDF or HTML file output: The base font size in px for body text (default: 10)
  --lineHeight <height>    For PDF or HTML file output: The line height (default: 1.6)
  --paperSize [Letter|Legal|A4|A3]
                           For PDF output: The paper size (default: Letter)
  --help
  --version
 --version
```

## FITS Keywords

The generated subsystem APIs contain infomation about FITS keywords whose values come from event parameters. That is, an event's parameter value is the source of the FITS keyword's value.

FITS keyword data is stored in three files under [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files/tree/master/FITS-Dictionary) on GitHub. Once DMS is published, the file should be automatically loaded by the `icdwebserver` or `icd-git --ingest` commands. Until then, the FITS keywords, channels and tags can be manually loaded into the icd database once by running (from the icd source directory):

```
icd-fits -i examples/3.0/FITS-Dictionary.json --ingestChannels examples/3.0/FITS-Channels.conf --ingestTags examples/3.0/FITS-Tags.conf
```

Alternatively you can check out and manually ingest  DMS-Model-Files into the local icd database by using the Upload feature in the icd web app or with the command line:

    icd-db -i DMS-Model-Files

The contents of the files are as follows:
* 
* `FITS-Dictionary.json` - This is the FITS dictionary and contains an entry for each FITS keyword, mapping it to source event parameters. If a keyword has multiple sources, named channels are used, each containing one source.
* `FITS-Channels.conf` - This defines which channels are available for each subsystem (Channels are used when a FITS keyword has multiple source event parameters).
* `FITS-Tags.conf` - Assigns tags to FITS keywords, which can be used in the web app to filter and display the FITS keyword information.

Besides the three above files, FITS keyword information can be defined in the `publish-model.conf` files for each subsystem component. Event parameters can define the associated keyword as follows:

    keyword = IMGDISWV

If the keyword has multiple source parameters, you can specify the channel:

    keyword = IMGDISWV
    channel = ATM

In some more complicated cases, you can also specify multiple keywords whose values are taken from an index (or rowIndex for matrix/2d arrays) in the parameter's array values:

```
keywords: [
     {
       keyword = OIWFS1PS
       rowIndex = 0
     }
     {
       keyword = OIWFS2PS
       rowIndex = 1
     }
     {
       keyword = OIWFS3PS
       rowIndex = 2
     }
]
```

The FITS keyword definitions in a subsystem's model files can be used to generate a new FITS dictionary by merging the existing FITS dictionary with the definitions in the publish model files. In this case the information from the published events overrides the information in the existing FITS dictionary:

    icd-fits --subsystem IRIS --generate FITS-Dictionary.json

Or using the short form options and with a subsystem version:

    icd-fits -s IRIS:1.7 -g FITS-Dictionary.json

The generated FITS dictionary JSON file can then be copied to the [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files/tree/master/FITS-Dictionary) repository and published, so that it will be automatically used by the icd web app and command line apps (Note that Publishing DMS-Model-files requires special permission).

You can also manually load the new FITS dictionary into your local icd database using the command line:

    icd-fits -i FITS-Dictionary.json

## Generating a Document listing the FITS Keywords

You can use `icd-fits` to print a list of keywords coming from a subsystem or component to stdout. For example, the following command lists the keywords for IRIS:

    icd-fits --subsystem IRIS --list

Or you can use the short form options and restrict the output to a component:

    icd-fits -s IRIS -c pupilview -l

You can create a PDF of the IRIS FITS keywords like this:

    icd-fits -s IRIS -o IRIS-Keywords.pdf

The format of the output file depends on the suffix. You can also generate `csv`, `html`, `json` and `conf` (HCON) formatted files with the same information.

