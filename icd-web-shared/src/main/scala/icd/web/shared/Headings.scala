package icd.web.shared

import scalatags.Text
import scalatags.Text.all._

object Headings {
  /**
    * Returns a unique id for a link target.
    *
    * @param component component name
    * @param action    publishes, subscribes, sends, receives
    * @param itemType  Event, Alarm, etc.
    * @param name      item name
    * @return the id
    */
  def idFor(component: String, action: String, itemType: String, name: String): String = {
    s"$component-$action-$itemType-$name".replace(" ", "-")
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
    val id = makeId(title, optionalId)
    h2(a(name := id)(title))
  }

  def H3(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val id = makeId(title, optionalId)
    h3(a(name := id)(title))
  }

  def H4(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val id = makeId(title, optionalId)
    h4(a(name := id)(title))
  }

  def H5(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val id = makeId(title, optionalId)
    h5(a(name := id)(title))
  }
}

