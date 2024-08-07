FITS Keywords
-------------

The generated APIs and ICDs contain infomation about FITS keywords that are based on event parameters.
The information is stored in two files under [DMS-Model-Files](https://github.com/tmt-icd/DMS-Model-Files/tree/master/FITS-Dictionary)
on GitHub. The file is automatically loaded from DMS-Model-Files by the `icdwebserver` or `icd-git --ingest` commands.
The FITS keywords and `tags` can also be manually loaded into the icd database once by running (from this directory):

```
icd-fits -i FITS-Dictionary.json --ingestTags FITS-Tags.conf
```


FITS Dictionary and Tag files
-------------------------------

The file FITS-Dictionary.json is an example of a file that is ingested into the icd database
and maps FITS keywords to the event parameters that are the source of the data.

FITS tags are used to group the keywords into categories.

The format of FITS-Dictionary.json is basically an array of two types of entries: simple FITS keywords
amd keywords that can come from different channels.

An example of the simple format is:

```json
    {
      "name": "CRPIX1",
      "description": "This is the X coordinate of the reference pixel in the image",
      "type": "float",
      "units": null,
      "source": {
        "subsystem": "TCS",
        "componentName": "PointingKernelAssembly",
        "eventName": "WCSFITSHeader",
        "parameterName": "CRPIX1"
      }
    },
```

If the keyword has multiple event parameter sources or channels, this format is used:

```json
    {
      "name": "NUMREADS",
      "description": "Number of raw readouts used in the sampling mode per ramp. This is the number of raw readouts that are added together before being used in the sampling mode to create a single frame. This number is only used in sample modes of MDS and UTR.",
      "type": "integer",
      "units": null,
      "channel": [
        {
          "name": "IFS",
          "comment": "Some text...",
          "source": {
            "subsystem": "IRIS",
            "componentName": "is",
            "eventName": "ifsObserveSettings",
            "parameterName": "numReads"
          }
        },
        {
          "name": "IMAGER",
          "comment": "Some other text",
          "source": {
            "subsystem": "IRIS",
            "componentName": "is",
            "eventName": "imagerObserveSettings",
            "parameterName": "numReads"
          }
        }
      ]
    }
```

The FITS-Tags.conf file defines the available tags for FITS keywords and lists which keywords have which tags.
A keyword can have multiple tags as well as multiple sources.

For example, the SL and DL tags can be defined as follows (The keyword assignment is just for testing at this point):

```
// SEEING-LIMITED
SL = [
  CLDSTPX
  CLDSTPY
  NUMREADS/IMAGER
  ...
]

// DIFFRACTION-LIMITED
DL = [
  AIRMASS
  AODM0TM
  NUMREADS/IFS
  ...
]
```

For keywords with a single source event parameter, it is enough to specify the keyword name.
If a keyword has multiple sources or channels, the name of the channel can be specified after a "/".
In the above example, the NUMREADS keyword can come from the IFS or IMAGER channels.
The IMAGER version of NUMREADS come from the `imagerObserveSettings` event while the 
IFS version comes from the `ifsObserveSettings` event.
