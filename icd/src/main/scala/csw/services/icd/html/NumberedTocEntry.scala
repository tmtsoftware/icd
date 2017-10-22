package csw.services.icd.html

import scalatags.Text
import scalatags.Text.all._
import scalatags.text.Builder

/**
  * Adds automatic numbering to TOC entries.
  */
class NumberedTocEntry {
  private var h2Counter = 0
  private var h3Counter = 0
  private var h4Counter = 0
  private var h5Counter = 0
  private var h6Counter = 0


  def toc2(attr: AttrPair)(body: String): Text.TypedTag[String] = {
    h2Counter = h2Counter + 1
    h3Counter = 0
    a(attr)(s"$h2Counter ", body)
  }

  def toc3(attr: AttrPair)(body: String): Text.TypedTag[String] = {
    h3Counter = h3Counter + 1
    h4Counter = 0
    a(attr)(s"$h2Counter.$h3Counter ", body)
  }

  def toc4(attr: AttrPair)(body: String): Text.TypedTag[String] = {
    h4Counter = h4Counter + 1
    h5Counter = 0
    a(attr)(s"$h2Counter.$h3Counter.$h4Counter ", body)
  }

  def toc5(attr: AttrPair)(body: String): Text.TypedTag[String] = {
    h5Counter = h5Counter + 1
    h6Counter = 0
    a(attr)(s"$h2Counter.$h3Counter.$h4Counter.$h5Counter ", body)
  }

  def toc6(attr: AttrPair)(body: String): Text.TypedTag[String] = {
    h6Counter = h6Counter + 1
    a(attr)(s"$h2Counter.$h3Counter.$h4Counter.$h5Counter.$h6Counter ", body)
  }
}
