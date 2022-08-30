package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.{FitsKeyInfo, FitsSource, PdfOptions}
import reactivemongo.api.bson._

object FitsSourceBsonParser {
  def apply(doc: BSONDocument): Option[FitsSource] = {
    try {
      Some(
        FitsSource(
          subsystem = doc.getAsOpt[String]("subsystem").get,
          componentName = doc.getAsOpt[String]("componentName").get,
          eventName = doc.getAsOpt[String]("eventName").get,
          parameterName = doc.getAsOpt[String]("parameterName").get,
          index = doc.getAsOpt[Int]("index"),
          rowIndex = doc.getAsOpt[Int]("rowIndex")
        )
      )
    }
    catch {
      case _: NoSuchElementException =>
        None
    }
  }
}

object FitsKeyInfoBsonParser {
  def getSource(doc: BSONDocument): List[FitsSource] = {
    val list =
      for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("source").map(_.toList).getOrElse(Nil))
        yield FitsSourceBsonParser(subDoc)
    list.flatten
  }

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): FitsKeyInfo = {
    FitsKeyInfo(
      name = doc.getAsOpt[String]("name").get,
      title = doc.getAsOpt[String]("title").get,
      description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      typ = doc.getAsOpt[String]("type").get,
      defaultValue = doc.getAsOpt[String]("defaultValue"),
      units = doc.getAsOpt[String]("units"),
      source = getSource(doc),
      note = doc.getAsOpt[String]("note")
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