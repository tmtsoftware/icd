package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.{FitsChannel, FitsKeyInfo, FitsSource, FitsTags, PdfOptions}
import reactivemongo.api.bson._

object FitsTagsBsonParser {
  def apply(doc: BSONDocument): FitsTags = {
    val map = doc.toMap.view.mapValues(_.asOpt[List[String]].getOrElse(Nil)).filterKeys(_ != "_id").toMap
    FitsTags(map)
  }
}

object FitsSourceBsonParser {
  def apply(doc: BSONDocument): FitsSource = {
    // XXX TODO FIXME: Enforce non-null values in json-schema and validate when importing
    FitsSource(
      subsystem = doc.getAsOpt[String]("subsystem").getOrElse(""),
      componentName = doc.getAsOpt[String]("componentName").getOrElse(""),
      eventName = doc.getAsOpt[String]("eventName").getOrElse(""),
      parameterName = doc.getAsOpt[String]("parameterName").getOrElse(""),
      index = doc.getAsOpt[Int]("index"),
      rowIndex = doc.getAsOpt[Int]("rowIndex")
    )
  }
}

object FitsChannelBsonParser {
  def apply(doc: BSONDocument): FitsChannel = {
    FitsChannel(
      source = doc.getAsOpt[BSONDocument]("source").map(FitsSourceBsonParser(_)).get,
      name = doc.getAsOpt[String]("name").getOrElse(""),
      comment = doc.getAsOpt[String]("comment").getOrElse("")
    )
  }
}

object FitsKeyInfoBsonParser {
  def getChannels(doc: BSONDocument): List[FitsChannel] = {
    for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("channel").map(_.toList).getOrElse(Nil))
      yield FitsChannelBsonParser(subDoc)
  }

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): FitsKeyInfo = {
    // XXX TODO FIXME: Enforce non-null source or channel in json-schema and validate when importing
    val channels = {
      if (doc.contains("channel")) getChannels(doc)
      else if (doc.contains("source")) List(FitsChannel(source = doc.getAsOpt[BSONDocument]("source").map(FitsSourceBsonParser(_)).get))
      else Nil
    }
    FitsKeyInfo(
      name = doc.getAsOpt[String]("name").get,
      description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      typ = doc.getAsOpt[String]("type").get,
      units = doc.getAsOpt[String]("units"),
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
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)
      getItems("fitsKeyInfo", FitsKeyInfoBsonParser.apply(_, maybePdfOptions))
    }
  }
}
