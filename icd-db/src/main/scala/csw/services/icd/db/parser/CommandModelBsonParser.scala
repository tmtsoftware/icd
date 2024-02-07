package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{CommandModel, ReceiveCommandModel, SendCommandModel}
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * Model for commands received: See resources/<version>/command-schema.conf
 */
object ReceiveCommandModelBsonParser {
  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): ReceiveCommandModel = {
    // For backward compatibility, allow "args" or "parameters"
    val argsKey = if (doc.contains("parameters")) "parameters" else "args"
    ReceiveCommandModel(
      name = doc.getAsOpt[String]("name").get,
      ref = doc.getAsOpt[String]("ref").getOrElse(""),
      refError = "",
      description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get, maybePdfOptions),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      preconditions = doc.getAsOpt[Array[String]]("preconditions").map(_.toList).getOrElse(Nil).map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)),
      postconditions = doc.getAsOpt[Array[String]]("postconditions").map(_.toList).getOrElse(Nil).map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)),
      requiredArgs = doc.getAsOpt[Array[String]]("requiredArgs").map(_.toList).getOrElse(Nil),
      parameters =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]](argsKey).map(_.toList).getOrElse(Nil))
          yield ParameterModelBsonParser(subDoc, maybePdfOptions),
      completionType = doc.getAsOpt[String]("completionType").getOrElse("immediate"),
      resultType =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("resultType").map(_.toList).getOrElse(Nil))
          yield ParameterModelBsonParser(subDoc, maybePdfOptions),
      completionConditions = doc.getAsOpt[Array[String]]("completionCondition").map(_.toList).getOrElse(Nil),
      role = doc.getAsOpt[String]("role")
    )
  }
}

/**
 * Model for commands sent
 */
object SendCommandModelBsonParser {
  def apply(doc: BSONDocument): SendCommandModel = {
    SendCommandModel(
      name = doc.getAsOpt[String]("name").get,
      subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
      component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get
    )
  }
}

/**
 * Model for commands
 */
object CommandModelBsonParser {
  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[CommandModel] = {
    if (doc.isEmpty) None
    else
      Some(
        CommandModel(
          subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
          description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
          receive =
            for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("receive").map(_.toList).getOrElse(Nil))
              yield ReceiveCommandModelBsonParser(subDoc, maybePdfOptions),
          send =
            for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("send").map(_.toList).getOrElse(Nil))
              yield SendCommandModelBsonParser(subDoc)
        )
      )
  }
}
