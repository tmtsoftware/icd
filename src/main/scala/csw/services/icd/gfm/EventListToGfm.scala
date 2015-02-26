package csw.services.icd.gfm

import csw.services.icd.model.JsonSchemaModel

/**
 * Converts a list of event descriptions to a GFM formatted string
 */
case class EventListToGfm(list: List[JsonSchemaModel], level: Level) extends Gfm {
  private val head = s"\n\n####${level(2)} Events\n"
  private val table = JsonSchemaListToGfm(list).gfm
  val gfm = s"$head$table"
}
