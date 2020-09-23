package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.EventModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson._

/**
 * See resources/event-schema.conf
 */
object EventModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): EventModel = {
    // For backward compatibility, allow "attributes" or "parameters"
    val attrKey = if (doc.contains("parameters")) "parameters" else "attributes"
    EventModel(
      name = doc.getAsOpt[String]("name").get,
      ref = doc.getAsOpt[String]("ref").getOrElse(""),
      refError = "",
      description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      maybeMaxRate = doc.getAsOpt[BSONNumberLike]("maxRate").map(_.toDouble.getOrElse(1.0)),
      archive = doc.getAsOpt[Boolean]("archive").getOrElse(false),
      archiveDuration = doc.getAsOpt[String]("archiveDuration").getOrElse(""),
      parameterList =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]](attrKey).map(_.toList).getOrElse(Nil))
          yield ParameterModelBsonParser(subDoc, maybePdfOptions),

    )
  }
}
