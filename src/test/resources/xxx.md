
#Interface Control Document: WFOS-ESW

 Version | Subsystem | WBS Id
 ---|---|---
20141121 | WFOS | TMT.INS.INST.WFOS.SWE


The Mount Control System (MCS) consists of the drive motors, control electronics, encoders, servo controllers and computer systems, power supplies and associated equipment necessary to move and precisely control the position of the telescope azimuth and elevation axes in response to demands.

The Hydrostatic Bearing System (HBS) consists of the bearings, pumps, oil lines and associated infrastructure necessary to implement hydrostatic oil-film support bearings on both telescope axes.

The Segment Handling System (SHS) consists of the mechanical, electromechanical and control systems that enable telescope mirror segments to be extracted from and installed into the telescope.  

    

---


##Component: filter

This is the metadata description of the WFOS filter Assembly


Component Type | Uses Time | Uses Events | Uses Configurations | Uses Properties
---|---|---|---|---
Assembly | false | true | true | false

    

---

##1 Publish

###1.1 Telemetry

Name|Description|Rate|Max Rate|Archive|Archive Rate
---|---|---|---|---|--- |
status1 | status1 description | 0 | 100 | true | 10 |
status2 | Status2 description | 10 | 10 | false | 0 |

####1.1.1 Attributes for Telemetry status1

Name|Description|Type|Default|Units
---|---|---|---|---
a1 | single value with min/max | integer | 10 | m |
a2 | array of float | array of number | [0, 0, 0, 0, 0] | mm |
a3 | enum choice | String: (red, green, blue) | green |  |


####1.1.2 Attributes for Telemetry status2

Name|Description|Type|Default|Units
---|---|---|---|---
a4 | An attribute that is a custom object | object | {field1=test, field2=10} |  |



####1.2 Events

Name|Description|Type|Default|Units
---|---|---|---|---
a1 | single value with min/max | integer |  | m |
a2 | array of float | array of number |  | mm |
a3 | enum choice | String: (red, green, blue) | green |  |


###1.3 Event Streams

Name|Description|Rate|Max Rate|Archive|Archive Rate
---|---|---|---|---|--- |
status1 | status1 description | 0 | 100 | true | 10 |
status2 | status2 description | 10 | 10 | false | 0 |

####1.3.1 Attributes for Event Streams status1

Name|Description|Type|Default|Units
---|---|---|---|---
a1 | single value with min/max | integer |  | m |
a2 | array of float | array of number |  | mm |
a3 | enum choice | String: (red, green, blue) | green |  |


####1.3.2 Attributes for Event Streams status2

Name|Description|Type|Default|Units
---|---|---|---|---
a4 | single value with min/max | integer |  | m |


###1.4 Alarms

Name|Description|Severity|Archive
---|---|---|---
alarm1 | First alarm | minor | false |
alarm2 | Second alarm | major | true |


###1.5 Health

Name|Description|Rate|Archive|Archive Rate|Max Rate|Value Type
---|---|---|---|---|---|---
health1 | First health item lkjsldf lkj s dlkj lkjsdf lkjsdlfkj kjhkjh Hjkjhas k kjash  *here* and _there_ and so lkj lkj lkj kjhas kjhaskdjhkasdjh | 0 | true | 10 | 100 | String: (good, ill, bad, unknown) |
health2 | Second health item  asklj lkjsd lkj sdflkj fsdlkj dsflkjsd lkjds lkjsdf lkjsdf lkjsdf | 0 | false | 1 | 10 | integer |

---

##2 Subscribe

###2.1 Telemetry

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS | blue.filter | 0 | 10 |
TCS | elevation | 10 | 10 |


###2.2 Events

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS | blue.filter | 0 | 10 |
TCS | elevation | 10 | 10 |


###2.3 Event Streams

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS | blue.filter | 0 | 10 |
TCS | elevation | 10 | 10 |


###2.4 Alarms

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS | blue.filter | 0 | 10 |
TCS | elevation | 10 | 10 |


###2.5 Health

Subsystem|Name|Required Rate|Max Rate
---|---|---|---
WFOS | blue.filter | 0 | 10 |
TCS | elevation | 10 | 10 |

---

##3 Commands

##3.1 Configuration: *cmd1*


###3.1.1 *cmd1* Requirements

* First requirement for cmd1
* Second requirement for cmd1

###3.1.1 *cmd1* Arguments


Name|Description|Type|Default|Units
---|---|---|---|---
a1 | a1 description | number |  |  |
a2 | a2 description | string | bob |  |
a3 | a3 description is an enum/choice | String: (red, blue, orange) | green |  |
a4 | a4 is an array of integers | array of integer |  | arcsec |


##3.2 Configuration: *crateSetup*


###3.2.1 *crateSetup* Requirements

* First requirement for crateSetup
* Second requirement for crateSetup

###3.2.1 *crateSetup* Arguments


Name|Description|Type|Default|Units
---|---|---|---|---
podVoltage | voltage to be used by crate during ops | integer |  |  |
dwellTime | time between request and start of activity | number |  | seconds |