package icd.web.shared

case class FitsSource(
    subsystem: String,
    componentName: String,
    eventName: String,
    parameterName: String,
    index: Option[Int],
    rowIndex: Option[Int]
) {

  def toShortString: String = {
    val s =
      if (index.nonEmpty) s"[${index.get}]"
      else if (rowIndex.nonEmpty) s"[row ${rowIndex.get}]"
      else ""
    s"$componentName-$eventName-$parameterName$s"
  }

  def toLongString: String = {
    s"$subsystem-${toShortString}"
  }

  // Ignore index args for comparison
  override def hashCode(): Int = (subsystem, componentName, eventName, parameterName).##

  // Ignore index args for comparison
  override def equals(obj: Any): Boolean = {
    obj match {
      case FitsSource(`subsystem`, `componentName`, `eventName`, `parameterName`, _, _) => true
      case _                                                                            => false
    }
  }
}

case class FitsKeyInfo(
    name: String,
    title: String,
    description: String,
    typ: String,
    defaultValue: Option[String] = None,
    units: Option[String] = None,
    source: List[FitsSource] = Nil,
    note: Option[String] = None
)

case class FitsKeyInfoList(fitsKeyInfo: List[FitsKeyInfo])
