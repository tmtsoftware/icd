// Example publish-model file using jsonnet syntax.

// Note that there is an Intellij Idea jsonnet plugin, but it does not support formatting.
// For formatting, you can use the "jsonnetfmt" command line app.

{
  subsystem: 'TEST',
  component: 'jsonnet.example',

  // comments can use // or #
  publish: {
    description: |||
      Multi-line descriptions
      use this syntax in jsonnet
    |||,

    // Defines a local function to generate a common event description based on the given arguments
    local commonEventFunc(eventName, paramName, typeName) = {
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

    events: [
      // define two similar events
      commonEventFunc('myEvent1', 'param1', 'boolean'),
      commonEventFunc('myEvent2', 'param2', 'string'),
      // define another event
      {
        name: 'heartbeat',
        parameters: [
          {
            name: 'heartbeat',
            description: 'software heartbeat',
            type: 'integer',
          },
        ],
      },

    ],
  },
}
