package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{CommandModel, ReceiveCommandModel, SendCommandModel}
import reactivemongo.api.bson._

/**
 * Model for commands received: See resources/command-schema.conf
 */
object ReceiveCommandModelBsonParser {
  def apply(doc: BSONDocument): ReceiveCommandModel = {
    ReceiveCommandModel(
      name = doc.getAsOpt[String]("name").get,
      description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      preconditions = doc.getAsOpt[Array[String]]("preconditions").map(_.toList).getOrElse(Nil).map(HtmlMarkup.gfmToHtml),
      postconditions = doc.getAsOpt[Array[String]]("postconditions").map(_.toList).getOrElse(Nil).map(HtmlMarkup.gfmToHtml),
      requiredArgs = doc.getAsOpt[Array[String]]("requiredArgs").map(_.toList).getOrElse(Nil),
      args =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("args").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),
      completionType = doc.getAsOpt[String]("completionType").getOrElse("immediate"),
      resultType =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("resultType").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),
      completionConditions = doc.getAsOpt[Array[String]]("completionCondition").map(_.toList).getOrElse(Nil)
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
  def apply(doc: BSONDocument): Option[CommandModel] = {
    if (doc.isEmpty) None
    else
      Some(
        CommandModel(
          subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
          description = doc.getAsOpt[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
          receive =
            for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("receive").map(_.toList).getOrElse(Nil))
              yield ReceiveCommandModelBsonParser(subDoc),
          send =
            for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("send").map(_.toList).getOrElse(Nil))
              yield SendCommandModelBsonParser(subDoc)
        )
      )
  }
}
