// Example publish-model file using jsonnet syntax.

// Note that there is an Intellij Idea jsonnet plugin, but it does not support formatting.
// For formatting, you can use the "jsonnetfmt" command line app.

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
            enum: ['EXPOSING', 'READING', 'IDLE', 'ERROR']
          }
        ]
      }
    ]
  }
}
