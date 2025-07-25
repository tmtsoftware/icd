package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{CommandModel, CommandResultModel, ReceiveCommandModel, SendCommandModel}
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * Model for commands received: See resources/<version>/command-schema.conf
 */
object ReceiveCommandModelBsonParser {
  private object CommandResultParser {
    def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): CommandResultModel = {
      CommandResultModel(
        description = HtmlMarkup.gfmToHtml(doc.string("description").get, maybePdfOptions),
        parameters =
          for (subDoc <- doc.children("parameters"))
            yield ParameterModelBsonParser(subDoc, maybePdfOptions, Map.empty, None, None)
      )
    }
  }

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): ReceiveCommandModel = {
    // For backward compatibility, allow "args" or "parameters"
    val argsKey = if (doc.contains("parameters")) "parameters" else "args"

    // For backward compatibility, handle resultType (a parameter list)
    val resultType =
      for (subDoc <- doc.children("resultType"))
        yield ParameterModelBsonParser(subDoc, maybePdfOptions)
    val maybeResultBackwardCompat = if (resultType.isEmpty) None else Some(CommandResultModel("", resultType))
    val maybeResult = doc.get("result").map(_.asInstanceOf[BSONDocument]).map(CommandResultParser(_, maybePdfOptions))

    ReceiveCommandModel(
      name = doc.string("name").get,
      ref = doc.string("ref").getOrElse(""),
      refError = "",
      description = HtmlMarkup.gfmToHtml(doc.string("description").get, maybePdfOptions),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      preconditions = doc.getAsOpt[Array[String]]("preconditions").map(_.toList).getOrElse(Nil).map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)),
      postconditions = doc.getAsOpt[Array[String]]("postconditions").map(_.toList).getOrElse(Nil).map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)),
      requiredArgs = doc.getAsOpt[Array[String]]("requiredArgs").map(_.toList).getOrElse(Nil),
      parameters =
        for (subDoc <- doc.children(argsKey))
          yield ParameterModelBsonParser(subDoc, maybePdfOptions),
      completionType = doc.string("completionType").getOrElse("immediate"),
      maybeResult = if (maybeResult.isDefined) maybeResult else maybeResultBackwardCompat,
      completionConditions = doc.getAsOpt[Array[String]]("completionCondition").map(_.toList).getOrElse(Nil),
      role = doc.string("role")
    )
  }
}

/**
 * Model for commands sent
 */
object SendCommandModelBsonParser {
  def apply(doc: BSONDocument): SendCommandModel = {
    SendCommandModel(
      name = doc.string("name").get,
      subsystem = doc.string(BaseModelBsonParser.subsystemKey).get,
      component = doc.string(BaseModelBsonParser.componentKey).get
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
          subsystem = doc.string(BaseModelBsonParser.subsystemKey).get,
          component = doc.string(BaseModelBsonParser.componentKey).get,
          description = doc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
          receive =
            for (subDoc <- doc.children("receive"))
              yield ReceiveCommandModelBsonParser(subDoc, maybePdfOptions),
          send =
            for (subDoc <- doc.children("send"))
              yield SendCommandModelBsonParser(subDoc)
        )
      )
  }
}
