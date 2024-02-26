# Model Files

In the OMOA, subsystem software consists of components: Hardware Control Daemons, Assemblies, Sequencers, and
Applications. Model files that provide information about the component and its interfaces are created for each of the
components. This section describes the model files. The model files are named: component-model, command-model,
publish-model, alarm-model, and subscribe-model. In addition, each subsystem has a single subsystem-model file and may
have one or more `$subsystem-icd-model` files that contain additional information about the interface between two
subsystems. Separate files were created (rather than one larger file) in order to keep the configuration files simpler
and easier to type.

The following table shows the different types of model files and what they describe:

| Model File                  | Description                                                                                                                                                                                                                                               |
|:----------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| subsystem-model.conf        | Contains high-level information about the subsystem that contains a group of components. There is one subsystem-model.conf file per subsystem.                                                                                                            |
| component-model.conf        | Contains high-level component information. There is one component-model.conf file per component.                                                                                                                                                          |
| command-model.conf          | Describes the configuration commands the component supports. Also describes components and commands the component may send to other components. There is at most one command-model.conf file per component.                                               |
| publish-model.conf          | Describes events the component publishes using Event Services. There is at most one publish-model.conf file per component.                                                                                                                                |
| service-model.conf          | Describes HTTP services provided or consumed by the component. The HTTP services themselves are described in OpenAPI files.                                                                                                                               |
| alarm-model.conf            | Describes alarms the component publishes using the Alarm Service. There is at most one alarm-model.conf file per component.                                                                                                                               |
| subscribe-model.conf        | Describes events the component subscribes to using Event Services. There is at most one subscribe-model.conf file per component.                                                                                                                          |
| *$subsystem*-icd-model.conf | Where *$subsystem* is the name of one of the other TMT subsystems, for example: IRIS-icd-model.conf. These files may contain additional information about the interface between the subsystem being defined and another subsystem (IRIS in this example). |


The component is only required to create a `command-model.conf`, `publish-model.conf`, `alarm-model.conf`, or `subscribe-model.conf` if it provides or uses the features described in the model files (i.e. if the component does not subscribe to events, then a `subscribe-model.conf` file is not needed).

Model files are text files written in a superset of JSON called [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md), which provides syntactic sugar for writing JSON, but can be exported as pure JSON. In addition, most fields support GitHub style MarkDown as well as HTML tags. LaTeX math markup and UML are also supported (see below). In addition, it is possible to reuse certain repetitive definitions, such as those for events, commands and parameters by using the `ref` keyword in the model files to refer to another definition. The syntax is described below. Each type of model file has a schema which specifies the allowed structure of the contents within, including specifying the optional and required items, item types, allowed ranges, etc. The following sections describe each of the model files including necessary fields.

## Markdown Support, Math, UML, Dot

In addition to using standard [GitHub style MarkDown](https://github.github.com/gfm/) in descriptions, you can also insert LaTeX math formulas:
Here is an example description text containing an inline math formula:

```
description = """
Here is an example using LaTeX math: $`\frac{d}{dx}\left( \int_{0}^{x} f(u)\,du\right)=f(x)`$.
And here is another: $`[x^n + y^n = z^n]`$.
“””
```

And this is the inline math formula displayed from the above input:

![](../images/modelFiles/exampleLaTeXMath.png)

Block math formulas are delimited by

    ```math
    
    ``` 

For example: 

    Description = “””
    Here is an example using LaTeX math: $`\frac{d}{dx}\left( \int_{0}^{x} f(u)\,du\right)=f(x)`$.
    And here is another: $`[x^n + y^n = z^n]`$.
    
    This is a math block:
    
    ```math
    $$\idotsint_V \mu(u_1,\dots,u_k) \,du_1 \dots du_k$$
    ```
    
    and another:
    
    ```math
    \frac{n!}{k!(n-k)!} = \binom{n}{k}
    ```
    
    and a matrix:
    
    ```math
    A_{m,n} =
     \begin{pmatrix}
      a_{1,1} & a_{1,2} & \cdots & a_{1,n} \\
      a_{2,1} & a_{2,2} & \cdots & a_{2,n} \\
      \vdots  & \vdots  & \ddots & \vdots  \\
      a_{m,1} & a_{m,2} & \cdots & a_{m,n}
     \end{pmatrix}
    ```
    “””


The display for the above description is shown below:

![](../images/modelFiles/html-math.png)

UML ([PlantUML](https://plantuml.com/)) and [Graphviz Dot](https://graphviz.org/) are also supported, delimited by 

    ```uml
    ``` 

For example, below are some embedded UML blocks in a model file description text:

    Description = “””
    
    and a small one:
    
    ```uml
    Bob -[#red]> Alice : hello
    Alice -[#0000FF]->Bob : ok
    ```
    
    Note that according to https://plantuml.com/dot you can also use Graphviz/Dot diagrams instead of UML:
    
    ```uml
    digraph foo {
      node [style=rounded]
      node1 [shape=box]
      node2 [fillcolor=yellow, style="rounded,filled", shape=diamond]
      node3 [shape=record, label="{ a | b | c }"]
    
      node1 -> node2 -> node3
    }
    ```
    """

Below is the display produced from the above description text:

![](../images/modelFiles/umlBlock.png)


## Inner-Document Links

It is possible to make inner-document links to existing anchors using Markdown syntax. The easiest way to see the syntax for the ids is to look at the generated HTML. For example, the output of:

    icd-db -s NFIRAOS -o NFIRAOS.html

Note that the name attribute is used in the generated HTML instead of id, since the PDF generator required that. Many of the anchors have the following syntax:

*$thisComponent-$action-$itemType-$subsystem.$component.$name* 

where
* *$thisComponent* is the component being described
* *$action* is one of {publishes, subscribes, sends, receives}
* *$itemType* is one of {Event, ObserveEvent, Alarm, Command}
* *$subsystem* is the subsystem for the item
* *$component* is the component for the item
* *$name* is the name of the item being published, subscribed to, or the command being sent or received

For example, to link to the description of a published event named `heartbeat` in the lgsWfs component in the TEST subsystem:

    See: [here](#lgsWfs-publishes-Event-TEST.lgsWfs.heartbeat). 

## Reusing Definitions for Events, Commands, Parameters (refs)

It is possible to reuse similar parts of event, command and parameter definitions by using the `ref` keyword. The example below uses a reference to an event (`engMode`) in another event (`engMode2`):

```
  events = [
    {
      name = engMode
      description = "LGS WFS engineering mode enabled"
      archive = false
      parameters = [
        ...
      ]
    }
    {
      name = engMode2
      description = "LGS WFS engineering mode 2 enabled"
      archive = true
      ref = engMode
    }
```

In the above example, the event `engMode2` will have the same settings and parameters as `engMode`, except for `description` and `archive`, which are overridden. Any fields, which are not set, are inherited from the referenced event. This works for events, commands and parameters, as shown in the parameter reference example below:

```
      parameters = [
        {
          name = mode3
          ref = engMode/parameters/mode
        }

```

In the above example, the parameter `mode3` will be exactly the same as the `mode` parameter in the `engMode` event in the same component. You could also specify a different description field or any other parameter fields that should override the ones defined for mode.

The syntax of the `ref` value is flexible and allows you to reference any event, command or parameter in any component within the same subsystem. You can use a full path to specify a reference to an item in another component, or an abbreviated path for items in the same scope. The full syntax of a ref is something like this:

*componentName/section/eventName[/parametersSection/paramName]*

For example, to reference an event, observe event or current state, use:

* componentName/events/eventName
* or componentName/observeEvents/eventName
* or componentName/currentState/eventName
* or events/eventName, ... (if in the same component)
* or just eventName (if in the same component and event type section)
For commands received, the syntax is similar:
* componentName/receive/commandName
* or just commandName (if in the same component)

The syntax for references to parameters of events adds the `parameters` keyword and the parameter name:

* componentName/events/eventName/parameters/paramName

or abbreviated as described above for events:

* observeEvents/eventName/parameters/paramName (in same component)
* or eventName/parameters/paramName (in same component and events section)

Or just paramName (if in the same parameters section)

The syntax for parameters of commands is similar. Here you need to specify if the parameters appear in the “parameters” section or in the “resultType”.

* componentName/receive/commandName/parameters/paramName
* or componentName/receive/commandName/resultType/paramName

Or abbreviated as described above.

See the [example model files](https://github.com/tmtsoftware/icd/blob/master/examples/3.0/TEST/lgsWfs/publish-model.conf) in the icd sources for some examples of the `ref` keyword.

@@@ note
If there is an error in the reference, the error message is displayed in the log output of the icd-db command, if it is used, and also inserted in the generated HTML or PDF document (in the details section).
@@@

Below is an example that demonstrates some of the `ref` syntax for event parameters:

```
  events = [
    {
      name = engMode3
      description = "LGS WFS engineering mode enabled 3"
      archive = true
      parameters = [
        {
          // Example reference to a parameter in this component
          name = mode3
          ref = engMode/parameters/mode
        }
        {
          // Example reference to a parameter in this component (using wrong ref to produce error in HTML/PDF output)
          name = mode3Error
          ref = engMode/parameters/modeXXX
        }
```

@@@ note

Note: An earlier version of the icd software used the terms "attributes" for events parameters and "args" for command parameters.
These have been renamed to "parameters" for compatibility with CSW, however for backward compatibility the previous names are also allowed in refs.

@@@


## Using Jsonnet for Model Files

Normally, the icd model files are written in [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format, a simplified JSON format, and have a `.conf` suffix. In some cases, you may want to have more features available, for example to refer to similar definitions in may different places. The icd software also supports model files using the [jsonnet](https://jsonnet.org/) data templating language, which lets you define variables and functions and reuse them. To do this, replace the `.conf` suffix with `.jsonnet` (or `.libsonnet` for jsonnet files you want to import) and follow the syntax rules for jsonnet, so that the resulting JSON, after being processed by jsonnet conforms to the icd JSON schema. The icd software will automatically preprocess the jsonnet model files when they are imported into the icd database.

For example, you can add some common, reusable, top level definitions in a file `utils.libsonnet`:

```
// Define a common event
{
  heartbeat: {
    name: 'heartbeat',
    description: 'Heartbeat event description...',
    parameters: [
      {
        name: 'heartbeat',
        description: 'software heartbeat',
        type: 'integer',
      },
    ],
  },

  // Defines a function to generate a common event model based on the given arguments
  commonEventFunc(eventName, paramName, typeName):: {
    name: eventName,
    description: eventName + ' description.',
    archive: false,
    parameters: [
      {
        name: paramName,
        description: 'Description for ' + paramName,
        type: typeName,
      },
    ],
  },
}
```

And then reference these definition in a `publish-model.jsonnet` file for a different component. Below is an example `publish-model.jsonnet` file that imports reusable jsonnet code:

```
// Import common function defined in another file
local utils = import '../utils.libsonnet';

{
  subsystem: 'TEST',
  component: 'jsonnet.example',

  // comments can use // or #
  publish: {
    description: |||
      Multi-line descriptions
      use this syntax in jsonnet
    |||,

    events: [
      // define two similar events using imported function
      utils.commonEventFunc('myEvent1', 'param1', 'boolean'),
      utils.commonEventFunc('myEvent2', 'param2', 'string'),

      // Insert an imported event definition
      utils.heartbeat,

      // define another event
      {
        name: 'state',
        // Import the description text
        description: importstr '../importRaw.txt'
,
        archive: false,
        parameters: [
          {
            name: 'state',
            description: 'Detector state',
            enum: ['EXPOSING', 'READING', 'IDLE', 'ERROR'],
          },
        ],
      },
    ],
  },
}
```

The above example defines two events (myEvent1 and myEvent2) that are similar, but configured based on the given function arguments. The heartbeat event is used as is. The state event is defined in the usual way (for JSON), but imports the description text from a file.


## Subsystem-model

The subsystem model describes the overall subsystem. There is one `subsystem-model.conf` file for each subsystem.

Each subsystem may consist of several components. The IDBS merges them to create a subsystem API or ICD.

As an example, the `subsystem-model.conf` JSON schema file is shown below. This schema file is being shown for reference.  Users are not expected to interact with the schema files directly, but rather use this manual to understand how to structure their model files. 

subsystem-model.conf JSON schema:
```
id: "http://csw.tmt.org/subsystem-schema#"
"$schema": "http://json-schema.org/draft-07/schema#"

description = "Defines the model describing a top level subsystem"
type = object
additionalProperties: false
required = [modelVersion, subsystem, title, description]
properties {
  _id {
    description = "Optional unique id (automatically generated)"
    type = string
  }
  _version {
    description = "Optional version (automatically generated)"
    type = integer
  }
  modelVersion {
    description = "The version of the model file as Major.Minor version"
    type = string
    pattern = "^[0-9]+\\.[0-9]+$"
  }
  subsystem {
    description = "The name of this Subsystem"
    include classpath("3.0/subsystem.conf")
  }
  title {
    description = "The title of this subsystem, for display"
    type = string
  }
  description {
    description = "A description of this subsystem"
    type = string
  }
}
```

The line starting with `required =` shows the required fields in the `subsystem.conf` file are: `modelVersion`, `subsystem`, `title`, and `description`. The following lines in the schema file describe each of the fields. Note also that `modelVersion` requires a pattern given by a regular expression. The modelVersion must be a value like Major.Minor with at least 1 digit on each side of the period such as 3.0 (The latest version).

The fields for `subsystem.conf` are shown in the table below. The field name, whether it is required, and any special notes for the field are shown. Notes include required formats or conventions for a value.

Required and optional fields for `subsystem.conf`:

| Field | Required? | Notes |
| ----: | :-------: | :---|
| modelVersion| yes | Must be a Major.Minor version with Major and Minor digits.  Currently these values are supported: 1.0, 2.0, 3.0 (the latest version).|
|subsystem| yes| Name of the subsystem. Must be the same as SE subsystem name.|
|title| yes| The title of the subsystem. Will be displayed in generated documents. The title can contain spaces and other marks but should be one line.|
|description| yes | A description of the subsystem. The description is in triple quotes for multi-lined text. Note that spaces between paragraphs are retained and the text can contain GitHub flavored Markdown as well as HTML markup.|

The *modelVersion* is the model version for the entire subsystem. Each component also has a `modelVersion` field. This allows each component to be updated independently and then the subsystem to be updated as a whole. 

The subsystem field is the subsystem name. It must be one of the subsystem abbreviations from the SE N2 document. The list is shown below.

```
// Enumeration including all available subsystems
enum = [
  ENC, // Enclosure
  SUM, // Summit Facilities
  STR, // Structure
  M2S, // M2 System
  M3S, // M3 System
  CLN, // Mirror Cleaning System
  TINS, // Test Instruments
  TCS, // Telescope Control System
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
  SCMS // Site Conditions Monitoring System
]
```

The example below will create a TCS subsystem. The description comes from the text of the TCS CoDR software design document. Triple quotes allow multi-line entries.

```
subsystem=TCS
title="TELESCOPE CONTROL SYSTEM (TCS)"
modelVersion="3.0"
description="""
The main functions of the TCS are: 
1) Points and tracks targets in various reference frames by generating position demands for subsystems and instruments. Generates pointing models to remove repeatable mechanical errors and applies pointing models terms to correct mount demands.
2) Implements a key part in acquisition, guiding and wavefront sensing 
…
"""
```

