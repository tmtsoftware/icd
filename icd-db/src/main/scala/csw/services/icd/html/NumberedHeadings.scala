package csw.services.icd.html

import icd.web.shared.Headings

import scalatags.Text
import scalatags.Text.all._

/**
 * Adds automatic numbering to HTML headings and,
 * as a side effect, saves entries for generating a TOC.
 */
class NumberedHeadings extends Headings {
  private var h2Counter = 0
  private var h3Counter = 0
  private var h4Counter = 0
  private var h5Counter = 0
  private var h6Counter = 0

  // An entry in the TOC
  case class TocEntry(id: String, title: String, var l: List[TocEntry])

  // Recursively generates the TOC from the given list of entries
  private def mkToc(e: List[TocEntry]): Text.TypedTag[String] = {
    if (e.isEmpty) ul()
    else {
      ul(
        e.reverse.map { child =>
          li(a(href := s"#${child.id}")(child.title), mkToc(child.l))
        }
      )
    }
  }

  // Root TOC entry
  private var toc: List[TocEntry] = Nil

  /**
   * Called after the headings have been generated and returns the TOC
   */
  def mkToc(): Text.TypedTag[String] = mkToc(toc)

  /**
   * A level 2 numbered heading
   */
  def H2(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h2Counter = h2Counter + 1
    h3Counter = 0
    val ns = s"$h2Counter $title"
    toc = TocEntry(ident, ns, Nil) :: toc
    h2(a(name := ident, id := ident)(ns))

  }

  def H3(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h3Counter = h3Counter + 1
    h4Counter = 0
    val ns = s"$h2Counter.$h3Counter $title"
    toc.head.l = TocEntry(ident, ns, Nil) :: toc.head.l
    h3(a(name := ident, id := ident)(ns))
  }

  def H4(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h4Counter = h4Counter + 1
    h5Counter = 0
    val ns = s"$h2Counter.$h3Counter.$h4Counter $title"
    toc.head.l.head.l = TocEntry(ident, ns, Nil) :: toc.head.l.head.l
    h4(a(name := ident, id := ident)(ns))
  }

  def H5(title: String, optionalId: String = ""): Text.TypedTag[String] = {
    val ident = makeId(title, optionalId)
    h5Counter = h5Counter + 1
    h6Counter = 0
    val ns = s"$h2Counter.$h3Counter.$h4Counter.$h5Counter $title"
    toc.head.l.head.l.head.l = TocEntry(ident, ns, Nil) :: toc.head.l.head.l.head.l
    h5(a(name := ident, id := ident)(ns))
  }
}
