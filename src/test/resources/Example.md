#Interface Control Document <br> WFOS-ESW

 Version | Subsystem | WBS Id
 ---|---|---
2014-11-21 | WFOS | TMT.INS.INST.WFOS.SWE

---

##Component: Filter

This is the metadata description of the WFOS filter Assembly.


Component Type | Uses Time | Uses Events | Uses Configurations | Uses Properties
---|---|---|---|---
Assembly | no | no | no | no

---

##1 Publish


###1.1 Telemetry

Name|Rate|Max Rate|Archive|Archive Rate
---|---|---|---|---
status1|0|100|Yes|10
status2|10|10|No|

####1.1.1 Attributes for status1

Name|Description|Type|Default|Units
---|---|---|---|---
a1|single value with min/max|integer (-2 ≤ i ≤ 22)||m
a2|array of float|array of numbers (length = 5)||mm
a3|enum choice|String: ("red", "green", "blue")|"blue"

####1.1.2 Attributes for status2

Name|Description|Type|Default|Units
---|---|---|---|---
a4|single value with min/max|integer (-2 ≤ i ≤ 22)|10|m


###1.2 Events

Name|Description|Type|Default|Units
---|---|---|---|---
a1|single value with min/max|integer (-2 ≤ i ≤ 22)||m
a2|array of float|array of numbers (length = 5)||mm
a3|enum choice|String: ("red", "green", "blue")|"green"



###1.3 Event Streams

Name|Rate|Max Rate|Archive|Archive Rate
---|---|---|---|---|---|---
status1|0|100|Yes|10
status2|10|10|No|

####1.3.1 Attributes for status1

Name|Description|Type|Default|Units
---|---|---|---|---
a1|single value with min/max|integer (-2 ≤ i ≤ 22)||m
a2|array of float|array of numbers (length = 5)||mm
a3|enum choice|String: ("red", "green", "blue")|"red"

####1.3.2 Attributes for status2

Name|Description|Type|Default|Units
---|---|---|---|---
a4|single value with min/max|integer (-2 ≤ i ≤ 22)|3|m



###1.4 Alarms

Name|Description|Severity|Archive
---|---|---|---
alarm1|First alarm|minor|Yes
alarm2|Second alarm|major|Yes



###1.5 Health

Name|Description|Value Type|Default|Rate|Max Rate|Archive|Archive Rate
---|---|---|---|---|---|---|---|---
health1|First health item|string ("good", "ill", "bad", "unknown")|good|0|100|Yes|10
health2|Second health item|string ("good", "ill", "bad", "unknown")|good|1|10|No|1


---


##2 Subscribe


###2.1 Telemetry

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS|blue.filter|0|10
TCS|elevation|10|10


###2.2 Events

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS|blue.filter|0|10
TCS|elevation|10|10



###2.3 Event Streams

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS|blue.filter|0|10
TCS|elevation|10|10



###2.4 Alarms

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS|blue.filter|0|10
TCS|elevation|10|10


###2.5 Health

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS|blue.filter|0|10
TCS|elevation|10|10


---

##3 Commands


###3.1 Configuration: *Cmd1*

####3.1.1 Requirements

* [INT-TCS-STR-0010] The communication between the TCS and the STR computing sub-systems shall occur over the TMT Control/Telemetry LAN or dedicated network segment (TBD).

* [INT-TCS-STR-0020] The TCS and STR control sub-systems shall communicate the contents described in this ICD using the protocol described in [RD1].

####3.1.1.1 *Cmd1* Arguments

Name|Description|Type|Default|Units
---|---|---|---|---
a1|single value with min/max|integer (-2 ≤ i ≤ 22)|5|m
arg2|array of float|array of numbers (length = 5)||mm
arg3|enum choice|String: ("red", "green", "blue")|"green"|
arg4|A string value|string|"bob"|




###3.2 Configuration: *CreateSetup*

####3.2.1 Requirements

* [INT-TCS-STR-0010] The communication between the TCS and the STR computing sub-systems shall occur over the TMT Control/Telemetry LAN or dedicated network segment (TBD).

* [INT-TCS-STR-0020] The TCS and STR control sub-systems shall communicate the contents described in this ICD using the protocol described in [RD1].

####3.2.1.1 *CreateSetup* Arguments

Name|Description|Type|Default|Units
---|---|---|---|---
podVoltage|voltage to be used by crate during ops|integer (5 ≤ i ≤ 25)|10|
dwellTime|time between request and start of activity|number|1|seconds
