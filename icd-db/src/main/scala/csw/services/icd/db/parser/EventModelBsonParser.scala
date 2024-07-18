package csw.services.icd.db.parser

import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.EventModel
import icd.web.shared.{PdfOptions, SubsystemWithVersion}
import reactivemongo.api.bson.*

/**
 * See resources/<version>/event-schema.conf
 */
object EventModelBsonParser {

  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap,
      maybeSv: Option[SubsystemWithVersion]
  ): EventModel = {
    // For backward compatibility, allow "attributes" or "parameters"
    val attrKey = if (doc.contains("parameters")) "parameters" else "attributes"
    val name    = doc.string("name").get
    EventModel(
      name = name,
      category = doc.string("category").getOrElse(""),
      ref = doc.string("ref").getOrElse(""),
      refError = "",
      description = doc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      maybeMaxRate = doc.getAsOpt[BSONNumberLike]("maxRate").map(_.toDouble.getOrElse(1.0)),
      archive = doc.booleanLike("archive").getOrElse(false),
      archiveDuration = doc.string("archiveDuration").getOrElse(""),
      parameterList =
        for (subDoc <- doc.children(attrKey))
          yield ParameterModelBsonParser(subDoc, maybePdfOptions, fitsKeyMap, maybeSv, Some(name))
    )
  }
}
