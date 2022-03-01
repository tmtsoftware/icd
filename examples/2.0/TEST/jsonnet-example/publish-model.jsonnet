# Example publish-model file using jsonnet syntax
{
    subsystem: "TEST",
    component: "jsonnet.example",

    // comments can use // or #
    publish: {
        description: |||
            Multi-line descriptions
            use this syntax in jsonnet
        |||,

          events: [
            {
              name: "myEvent1",
              description: "Test event 1 description.",
              archive: false,
              parameters: [
                {
                  name: "mode",
                  description: "engineering mode enabled",
                  type: "boolean"
                }
              ]
            }
            ]

    }
}
