package icd.web.shared

// Holds a map from tag to list of FITS keywords
case class FitsTags(tags: Map[String, List[String]])

// Source parameter of the FITS keyword
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

  def toLongString: String = s"$subsystem-$toShortString"

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

// Multiple channels are used if a keyword comes from different sources, such as IRIS-IFS and IRIS-Imager.
// If there is only one channel, name and comment can be empty (generated automatically when parsing
// entries without the "channel" entry that only contain a "source".
case class FitsChannel(source: FitsSource, name: String = "", comment: String = "")

object FitsKeyInfo {
  implicit def orderingByName[A <: FitsKeyInfo]: Ordering[A] = Ordering.by(e => e.name)
}

// Information about one FITS keyword
case class FitsKeyInfo(
    name: String,
    description: String,
    typ: String,
    units: Option[String] = None,
    channels: List[FitsChannel] = Nil
)

case class FitsKeyInfoList(fitsKeyInfo: List[FitsKeyInfo])
