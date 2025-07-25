// This is an example jsonnet library file that contains definitions that can be
// imported in jsonnet based icd model files (icd model files with the .jsonnet suffix
// that are preprocessed with with jsonnet to produce the JSON stored in the icd database).
// Note that jsonnet library files have the suffix '.libsonnet', while jsonnet model files
// use '.jsonnet'.

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
    category: 'CONTROL',
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
