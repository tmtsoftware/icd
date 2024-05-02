package icd.web.shared

import scalatags.Text
import scalatags.Text.all.*

object Headings {

  /**
   * Returns a unique id for a link target.
   *
   * @param thisComponent component name (Component being described)
   * @param action    publishes, subscribes, sends, receives, provides, requires
   * @param itemType  Event, Alarm, etc.
   * @param subsystem the item's subsystem
   * @param component the item's component name
   * @param name      item name
   * @return the id
   */
  def idFor(
      thisComponent: String,
      action: String,
      itemType: String,
      subsystem: String,
      component: String,
      name: String,
  ): String = {
    val iType = if (itemType.endsWith("s")) itemType.dropRight(1) else itemType
    s"$thisComponent-$action-$iType-$subsystem.$component.$name".replace(" ", "-")
  }

  /**
   * Returns a unique id for a link target (with a parameter name).
   *
   * @param thisComponent component name (Component being described)
   * @param action    publishes, subscribes, sends, receives
   * @param itemType  Event, Alarm, etc.
   * @param subsystem the item's subsystem
   * @param component the item's component name
   * @param name      item name
   * @param paramName parameter name
   * @return the id
   */
  def idForParam(
      thisComponent: String,
      action: String,
      itemType: String,
      subsystem: String,
      component: String,
      name: String,
      paramName: String
  ): String = {
    val id = idFor(thisComponent, action, itemType, subsystem, component, name)
    s"$id.${paramName.replace(" ", "-")}"
  }
}

/**
 * Common trait for classes that provide headings (plain HTML or numbered headings).
 */
trait Headings {
  protected def makeId(title: String, optionalId: String): String =
    if (optionalId.nonEmpty) optionalId else title.replace(' ', '-')

  def H2(title: String, optionalId: String = ""): Text.TypedTag[String]

  def H3(title: String, optionalId: String = ""): Text.TypedTag[String]

  def H4(title: String, optionalId: String = ""): Text.TypedTag[String]

  def H5(title: String, optionalId: String = ""): Text.TypedTag[String]
}

/**
 * Plain, unnumbered html headings.
 */
class HtmlHeadings extends Headings {
  // Note: need to use "name :=" instead of "id :=" to make links work in PDFs with itextpdf-5.x!

  def H2(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h2(a(name := ident)(title))
  }

  def H3(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h3(a(name := ident)(title))
  }

  def H4(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h4(a(name := ident)(title))
  }

  def H5(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h5(a(name := ident)(title))
  }
}
