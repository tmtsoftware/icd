#Interface Control Document <br> WFOS-ESW

 Version | Subsystem | WBS Id
 ---|---|---
2014-11-21 | WFOS | TMT.INS.INST.WFOS.SWE


##Component: Filter

This is the metadata description of the WFOS filter Assembly



Component Type | Uses Time | Uses Events | Uses Configurations | Uses Properties
---|---|---|---|---
Assembly | no | no | no | no


##1 Publish

###1.1 Telemetry

Name|Rate|Max Rate|Archive|Archive Rate|Attributes
---|
status1|0|100|Yes|10|[see below][Attributes for status1]
status2|10|10|No||[see below][1.1.2]

####1.1.1 Attributes for status1

Name|Description|Type|Units
---|
a1|single value with min/max|integer (-2 ≤ i ≤ 22)|m
a2|array of float|array of numbers (length = 5)|mm
a3|enum choice|enum[red, green, blue]

####1.1.2 Attributes for status2

Name|Description|Type|Units
---|
a1|single value with min/max|integer (-2 ≤ i ≤ 22)|m
a2|array of float|array of numbers (length = 5)|mm
a3|enum choice|enum[red, green, blue]

###1.2 Events



```json
publish {

  events = [
    {
      name = a1
      description = "single value with min/max"
      type = integer
      minimum = -2
      maximum = 22
      units = m
    }
    {
      name = a2
      description = "array of float"
      type = array
      items = {
        type = number
      }
      minItems = 5
      maxItems = 5
      units = mm
    }
    {
      name = a3
      description = "enum choice"
      enum: [red, green, blue]
      default = green
    }
  ]

  eventStreams = [
    {
      name = "status1"
      rate = 0
      maxRate = 100
      archive = Yes
      archiveRate = 10
      attributes = [
        {
          name = a1
          description = "single value with min/max"
          type = integer
          minimum = -2
          maximum = 22
          units = m
        }
        {
          name = a2
          description = "array of float"
          type = array
          items = {
            type = number
          }
          minItems = 5
          maxItems = 5
          units = mm
        }
        {
          name = a3
          description = "enum choice"
          enum: [red, green, blue]
          default = green
        }
      ]
    }
    {
      name = "status2"
      rate = 10
      maxRate = 10
      archive = No
    }
  ]

  alarms = [
    {
      name = "alarm1"
      description = "First alarm"
      severity = minor
      archive = Yes
    }
  ]

  health = [
    {
      name = "health1"
      description = "First health item"
      valueType {
        enum = [good, ill, bad, unknown]
        default = good
      }
      rate = 0
      maxRate = 100
      archive = Yes
      archiveRate = 10
    }
  ]

}

```
