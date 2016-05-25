package csw.services.icd.html

import java.util.UUID

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

import scalatags.Text.all._
import scalatags.Text.TypedTag

/**
 * Defines HTML markup
 */
trait HtmlMarkup {
  protected val idStr = UUID.randomUUID().toString

  /**
   * Returns an HTML heading with the given depth, text and id
   */
  protected def mkHeading(depth: Int, text: String) = {
    val heading = s"h$depth".tag[String]
    heading(a(name := idStr)(text))
  }

  /**
   * Returns an HTML table of contents entry for the text and id
   */
  protected def mkTocEntry(text: String) = {
    import scalatags.Text.all._
    li(a(href := s"#$idStr")(text.trim))
  }

  /**
   * List of HTML tags to display (in order)
   */
  val tags: List[TypedTag[String]]

  /**
   * Optional entry to include in TOC
   */
  val tocEntry: Option[TypedTag[String]]

  /**
   * The HTML markup for a document part
   */
  def markup: TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "nopagebreak")(tags)
  }
}

/**
 * HTML markup utils
 */
object HtmlMarkup {

  private def isEmpty(x: String): Boolean = Option(x).forall(_.isEmpty)

  def formatRate(rate: Double): String = if (rate == 0) "" else s"$rate Hz"

  // Strips leading spaces from each line, since people don't realize that indenting is like ``` blocks in markdown.
  // Note: We could preserve the leading spaces after "|", but that was thought to be too scala specific...
  private def stripLeadingWs(s: String): String = {
    s.stripMargin.lines.map(_.trim).toList.mkString("\n")
  }

  /**
   * Returns the HTML snippet for the given markdown (GFM)
   *
   * @param gfm the Git formatted markdown
   */
  def gfmToHtml(gfm: String): String = {
    import org.pegdown.{Extensions, PegDownProcessor}
    if (isEmpty(gfm)) ""
    else {
      val pd = new PegDownProcessor(
        Extensions.TABLES | Extensions.AUTOLINKS | Extensions.FENCED_CODE_BLOCKS
          | Extensions.STRIKETHROUGH | Extensions.ATXHEADERSPACE | Extensions.TASKLISTITEMS,
        Long.MaxValue
      ) // last arg is to avoid pegdown timeouts

      // Convert markdown to HTML, then clean it up with jsoup to avoid issues with the pdf generator (and for security)
      Jsoup.clean(pd.markdownToHtml(stripLeadingWs(gfm)), Whitelist.basicWithImages())
    }
  }

  /**
   * Returns an HTML paragraph for the text (assumes markdown content was already converted to HTML)
   */
  def mkParagraph(text: String) = div(raw(text))

  /**
   * Returns an HTML paragraph containing the markup
   */
  def mkParagraph(markup: TypedTag[String]) = p(markup)

  /**
   * Returns an HTML paragraph for the given markup and text
   */
  def mkParagraph(markup: TypedTag[String], text: String) = div(markup, text)

  def bold(text: String) = strong(text)

  def italic(text: String) = em(text)

  def yesNo(b: Boolean): String = if (b) "yes" else "no"

  /**
   * Returns a HTML table with the given column headings and list of rows
   */
  def mkTable(head: List[String], rows: List[List[String]]) = {
    if (rows.isEmpty) p("n/a")
    else {
      val (newHead, newRows) = compact(head, rows)
      table(
        thead(
          tr(newHead.map(th(_)))
        ),
        tbody(
          for (row ← newRows) yield {
            tr(row.map(mkTableCell))
          }
        )
      )
    }
  }

  // Returns a table cell markup, checking if the text is already in html format (after markdown processing)
  private def mkTableCell(text: String) = {
    if (text.startsWith("<"))
      td(raw(text))
    else
      td(text)
  }

  // Removes any columns that do not contain any values
  private def compact(head: List[String], rows: List[List[String]]): (List[String], List[List[String]]) = {
    def notAllEmpty(rows: List[List[String]], i: Int): Boolean = {
      val l = for (r ← rows) yield r(i).length
      l.sum != 0
    }
    val hh = for {
      (h, i) ← head.zipWithIndex
      if notAllEmpty(rows, i)
    } yield (h, i)
    if (hh.length == head.length) {
      (head, rows)
    } else {
      val newHead = hh.map(_._1)
      val indexes = hh.map(_._2)
      def newRow(row: List[String]): List[String] = {
        row.zipWithIndex.filter(p ⇒ indexes.contains(p._2)).map(_._1)
      }
      val newRows = rows.map(newRow)
      (newHead, newRows)
    }
  }

  /**
   * Returns an HTML list of items
   */
  def mkList(list: List[String]) = ul(list.map(li(_)))
}
