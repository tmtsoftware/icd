package csw.services.icd.db.parser

import icd.web.shared.{AvailableChannels, FitsChannel, FitsKeyInfo, FitsKeyword, FitsSource, FitsTags, PdfOptions}
import org.apache.commons.text.StringEscapeUtils
import reactivemongo.api.bson.*
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

// keywords: A list of FITS keywords (or keyword/channel)
// inherit: A list of tags whose keywords should also be included
private case class FitsTagEntry(inherit: List[String], keywords: List[String])

// Parses the value of one tag in FITS-Tags.conf
private object FitsTagEntryBsonParser {
  def apply(doc: BSONDocument): FitsTagEntry = {
    val inherit  = doc.getAsOpt[Array[String]]("inherit").map(_.toList).getOrElse(Nil)
    val keywords = doc.getAsOpt[Array[String]]("keywords").map(_.toList).getOrElse(Nil)
    FitsTagEntry(inherit, keywords)
  }
}

// Parses FITS-Tags.conf
object FitsTagsBsonParser {
  // For a key in the format key/channel or just key, return the key and optional channel
  private def getKeyChannel(s: String): (String, Option[String]) = {
    if (s.contains("/")) {
      val a = s.split("/")
      (a.head, Some(a.tail.head))
    }
    else (s, None)
  }

  def apply(doc: BSONDocument): FitsTags = {
    val docMap   = doc.toMap.view.filterKeys(_ != "_id").mapValues(_.asOpt[BSONDocument].get).toMap
    val entryMap = docMap.map(p => (p._1, FitsTagEntryBsonParser(p._2)))
    val tags = entryMap.view.map { pair =>
      val tag   = pair._1
      val entry = pair._2
      //FitsKeyword
      val inheritList = entry.inherit.flatMap { inheritTag =>
        entryMap.get(inheritTag).toList.flatMap(_.keywords).map { s =>
          val (key, maybeChannel) = getKeyChannel(s)
          FitsKeyword(key, inheritTag, maybeChannel)
        }
      }
      val keyList = entry.keywords.map { s =>
        val (key, maybeChannel) = getKeyChannel(s)
        FitsKeyword(key, tag, maybeChannel)
      }
      (tag, (inheritList ++ keyList).sorted)
    }
    FitsTags(tags.toMap)
  }
}

object FitsSourceBsonParser {
  def apply(doc: BSONDocument): FitsSource = {
    // XXX TODO FIXME: Enforce non-null values in json-schema and validate when importing
    FitsSource(
      subsystem = doc.string("subsystem").getOrElse(""),
      componentName = doc.string("componentName").getOrElse(""),
      eventName = doc.string("eventName").getOrElse(""),
      parameterName = doc.string("parameterName").getOrElse(""),
      index = doc.int("index"),
      rowIndex = doc.int("rowIndex")
    )
  }
}

object FitsChannelBsonParser {
  def apply(doc: BSONDocument): FitsChannel = {
    FitsChannel(
      source = doc.getAsOpt[BSONDocument]("source").map(FitsSourceBsonParser(_)).get,
      name = doc.string("name").getOrElse(""),
      comment = doc.string("comment").getOrElse("")
    )
  }
}

object AvailableChannelsBsonParser {
  def apply(doc: BSONDocument): AvailableChannels = {
    AvailableChannels(
      subsystem = doc.string("subsystem").get,
      channels = doc.getAsOpt[Array[String]]("channels").map(_.toList).getOrElse(Nil)
    )
  }
}

/**
 * See resources/<version>/fits-channels-schema.conf
 */
object AvailableChannelsListBsonParser {
  def apply(doc: BSONDocument): List[AvailableChannels] = {
    if (doc.isEmpty) Nil
    else {
      def getItems[A](name: String, f: BSONDocument => A): List[A] =
        for (subDoc <- doc.children(name)) yield f(subDoc)
      getItems("availableChannels", AvailableChannelsBsonParser.apply)
    }
  }
}


object FitsKeyInfoBsonParser {
  private def getChannels(doc: BSONDocument): List[FitsChannel] = {
    for (subDoc <- doc.children("channel"))
      yield FitsChannelBsonParser(subDoc)
  }

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): FitsKeyInfo = {
    // XXX TODO FIXME: Enforce non-null source or channel in json-schema and validate when importing
    val channels = {
      if (doc.contains("channel")) getChannels(doc)
      else if (doc.contains("source"))
        List(FitsChannel(source = doc.getAsOpt[BSONDocument]("source").map(FitsSourceBsonParser(_)).get))
      else Nil
    }
    FitsKeyInfo(
      name = doc.string("name").get,
//      description = doc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      description = doc.string("description").map(StringEscapeUtils.escapeHtml4).getOrElse(""),
      `type` = doc.string("type").get,
      units = doc.string("units"),
      channels = channels
    )
  }
}

/**
 * See resources/<version>/fits-key-info-schema.conf
 */
object FitsKeyInfoListBsonParser {
  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): List[FitsKeyInfo] = {
    if (doc.isEmpty) Nil
    else {
      def getItems[A](name: String, f: BSONDocument => A): List[A] =
        for (subDoc <- doc.children(name)) yield f(subDoc)
      getItems("fitsKeyInfo", FitsKeyInfoBsonParser.apply(_, maybePdfOptions))
    }
  }
}
