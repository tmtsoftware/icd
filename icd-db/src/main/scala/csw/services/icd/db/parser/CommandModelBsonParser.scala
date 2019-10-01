package csw.services.icd.db.parser

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{CommandModel, ReceiveCommandModel, SendCommandModel}
import reactivemongo.bson.{BSONDocument, BSONValue}

/**
 * Model for commands received: See resources/command-schema.conf
 */
object ReceiveCommandModelBsonParser {
  def apply(doc: BSONDocument): ReceiveCommandModel = {
    ReceiveCommandModel(
      name = doc.getAs[String]("name").get,
      description = HtmlMarkup.gfmToHtml(doc.getAs[String]("description").get),
      requirements = doc.getAs[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      preconditions = doc.getAs[Array[String]]("preconditions").map(_.toList).getOrElse(Nil).map(HtmlMarkup.gfmToHtml),
      postconditions = doc.getAs[Array[String]]("postconditions").map(_.toList).getOrElse(Nil).map(HtmlMarkup.gfmToHtml),
      requiredArgs = doc.getAs[Array[String]]("requiredArgs").map(_.toList).getOrElse(Nil),
      args =
        for (subDoc <- doc.getAs[Array[BSONDocument]]("args").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),
      completionType = doc.getAs[String]("completionType").getOrElse("immediate"),
      resultType =
        for (subDoc <- doc.getAs[Array[BSONDocument]]("resultType").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),
      completionConditions = doc.getAs[Array[String]]("completionCondition").map(_.toList).getOrElse(Nil)
    )
  }
}

/**
 * Model for commands sent
 */
object SendCommandModelBsonParser {
  def apply(doc: BSONDocument): SendCommandModel = {
    SendCommandModel(
      name = doc.getAs[String]("name").get,
      subsystem = doc.getAs[String](BaseModelBsonParser.subsystemKey).get,
      component = doc.getAs[String](BaseModelBsonParser.componentKey).get
    )
  }
}

/**
 * Model for commands
 */
object CommandModelBsonParser {
  def apply(doc: BSONDocument): CommandModel = {
    CommandModel(
      subsystem = doc.getAs[String](BaseModelBsonParser.subsystemKey).get,
      component = doc.getAs[String](BaseModelBsonParser.componentKey).get,
      description = doc.getAs[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      receive =
        for (subDoc <- doc.getAs[Array[BSONDocument]]("receive").map(_.toList).getOrElse(Nil))
          yield ReceiveCommandModelBsonParser(subDoc),
      send =
        for (subDoc <- doc.getAs[Array[BSONDocument]]("send").map(_.toList).getOrElse(Nil))
          yield SendCommandModelBsonParser(subDoc)
    )
  }
}
