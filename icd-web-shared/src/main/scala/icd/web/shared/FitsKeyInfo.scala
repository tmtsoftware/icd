package icd.web.shared

case class FitsKeywordAndChannel(keyword: String, channel: Option[String])
object FitsKeywordAndChannel {
  implicit def orderingByName[A <: FitsKeywordAndChannel]: Ordering[A] = Ordering.by(e => e.keyword + e.channel)
}

case class FitsKeyword(keyword: String, tag: String, channel: Option[String])
object FitsKeyword {
  implicit def orderingByName[A <: FitsKeyword]: Ordering[A] = Ordering.by(e => e.keyword + e.tag)
}

// tags: A map from tag to list of FITS keywords (or keyword/channel)
//case class FitsTags(tags: Map[String, List[String]])
case class FitsTags(tags: Map[String, List[FitsKeyword]])

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

  // Called when reading abbreviated json FITS dictionary entry with only one source
  def fromSourceOrChannel(
      name: String,
      description: String,
      `type`: String,
      units: Option[String],
      maybeSource: Option[FitsSource],
      maybeChannels: Option[List[FitsChannel]]
  ): FitsKeyInfo = {
    if (maybeChannels.isDefined)
      FitsKeyInfo(name, description, `type`, units, maybeChannels.get)
    else
      FitsKeyInfo(name, description, `type`, units, List(FitsChannel(maybeSource.get)))
  }
}

// Available channels for a given subsystem
// (Channel names that may be used in publish-model.conf file to specify the FITS keyword channel
// for values coming from an event parameter)
case class AvailableChannels(
    subsystem: String,
    channels: List[String]
)

// FITS key information supplied as part of an event parameter description (in publish-model.conf)
case class EventParameterFitsKeyInfo(
    // FITS Key name
    name: String,
    // Optional channel name (if the same keyword has multiple source parameters)
    channel: Option[String] = None,
    // Optional index into array (if parameter value is an array)
    index: Option[Int] = None,
    // Optional row index, if parameter is a matrix/2d array
    rowIndex: Option[Int] = None
)

// Information about one FITS keyword
case class FitsKeyInfo(
    name: String,
    description: String,
    `type`: String,
    units: Option[String],
    channels: List[FitsChannel]
)

case class FitsKeyInfoList(fitsKeyInfo: List[FitsKeyInfo])

case class FitsDictionary(fitsKeys: List[FitsKeyInfo], fitsTags: FitsTags)
