package csw.services.icd.html

import scalatags.Text
import scalatags.Text.all._

/**
  * Adds automatic numbering to HTML headings.
  */
class NumberedHeadings {
  private var h2Counter = 0
  private var h3Counter = 0
  private var h4Counter = 0
  private var h5Counter = 0
  private var h6Counter = 0


  def H2(content: Text.TypedTag[String]): Text.TypedTag[String] = {
    h2Counter = h2Counter + 1
    h3Counter = 0
    h2(s"$h2Counter ", content)
  }

  def H3(content: Text.TypedTag[String]): Text.TypedTag[String] = {
    h3Counter = h3Counter + 1
    h4Counter = 0
    h3(s"$h2Counter.$h3Counter ", content)
  }

  def H4(content: Text.TypedTag[String]): Text.TypedTag[String] = {
    h4Counter = h4Counter + 1
    h5Counter = 0
    h4(s"$h2Counter.$h3Counter.$h4Counter ", content)
  }

  def H5(content: Text.TypedTag[String]): Text.TypedTag[String] = {
    h5Counter = h5Counter + 1
    h6Counter = 0
    h5(s"$h2Counter.$h3Counter.$h4Counter.$h5Counter ", content)
  }

  def H6(content: Text.TypedTag[String]): Text.TypedTag[String] = {
    h6Counter = h6Counter + 1
    h6(s"$h2Counter.$h3Counter.$h4Counter.$h5Counter.$h6Counter ", content)
  }
}
