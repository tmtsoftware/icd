package icd.web.shared

case class FitsSource(
    subsystem: String,
    componentName: String,
    eventName: String,
    parameterName: String,
    index: Option[Int],
    rowIndex: Option[Int]
)

case class FitsKeyInfo(
    name: String,
    title: String,
    description: String,
    typ: String,
    calculation: Option[String] = None,
    defaultValue: Option[String] = None,
    example: Option[String] = None,
    units: Option[String] = None,
    mandatory: Option[String] = None,
    minimal: Option[String] = None,
    position: Option[String] = None,
    fitsHdu: Option[String] = None,
    source: List[FitsSource] = Nil,
    Note: Option[String] = None
)

case class FitsKeyInfoList(fitsKeyInfo: List[FitsKeyInfo])
