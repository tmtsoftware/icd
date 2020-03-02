package icd.web.shared

import scalatags.Text
import scalatags.Text.all._

object Headings {

  /**
   * Returns a unique id for a link target.
   *
   * @param thisComponent component name (Component being described)
   * @param action    publishes, subscribes, sends, receives
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
    s"$thisComponent-$action-$itemType-$subsystem.$component.$name".replace(" ", "-")
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

  def H2(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h2(a(id := ident)(title))
  }

  def H3(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h3(a(id := ident)(title))
  }

  def H4(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h4(a(id := ident)(title))
  }

  def H5(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h5(a(id := ident)(title))
  }
}
