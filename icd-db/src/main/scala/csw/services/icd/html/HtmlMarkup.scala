package csw.services.icd.html

import java.util.UUID

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.{Parser, PegdownExtensions}
import com.vladsch.flexmark.profiles.pegdown.{Extensions, PegdownOptionsAdapter}
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.jsoup.nodes.Document.OutputSettings
import scalatags.Text.all._
import scalatags.Text.TypedTag

/**
 * Defines HTML markup
 */
trait HtmlMarkup {
  protected val idStr: String = UUID.randomUUID().toString

  /**
   * Returns an HTML heading with the given depth, text and id
   */
  protected def mkHeading(depth: Int, text: String): TypedTag[String] = {
    val heading = tag(s"h$depth")
    heading(a(name := idStr)(text))
  }

  /**
   * Returns an HTML table of contents entry for the text and id
   */
  protected def mkTocEntry(text: String): TypedTag[String] = {
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
//noinspection TypeAnnotation
object HtmlMarkup extends Extensions {

  private def isEmpty(x: String): Boolean = Option(x).forall(_.isEmpty)

  // Markdown to HTML support
  private val options = PegdownOptionsAdapter.flexmarkOptions(
    PegdownExtensions.TABLES | PegdownExtensions.AUTOLINKS | PegdownExtensions.FENCED_CODE_BLOCKS
      | PegdownExtensions.STRIKETHROUGH | PegdownExtensions.ATXHEADERSPACE | PegdownExtensions.TASKLISTITEMS
  )

  private val parser   = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build()

  // Enforce self-closing tags (e.g. for <img> tags) so that PDF generator will not fail.
  private val os = new OutputSettings().syntax(OutputSettings.Syntax.xml)

  def formatRate(rate: Double): String = if (rate == 0) "" else s"$rate Hz"

  def formatRate(compName: String, rate: Double): String = if (rate == 0) "" else s"<strong>$compName:</strong> $rate Hz"

  // Strips leading spaces from each line, since people don't realize that indenting is like ``` blocks in markdown.
  // Note: We could preserve the leading spaces after "|", but that was thought to be too scala specific...
  private def stripLeadingWs(s: String): String = {
    s.stripMargin.linesIterator.toList.map(_.trim).mkString("\n")
  }

  /**
   * Returns the HTML snippet for the given markdown (GFM)
   *
   * Note: Profiling and tests show that lots of time is spent in pd.markdownToHtml(),
   * but attempts to avoid calling it when the text looks simple did not improve peformance much.
   *
   * @param gfm the Git formatted markdown
   */
  def gfmToHtml(gfm: String): String = {
    if (isEmpty(gfm)) ""
    else
      try {
        // Convert markdown to HTML
        // Note: About 1/2 the time for generating the HTML for an ICD is spent parsing MarkDown strings
        val s = stripLeadingWs(gfm)
        val html = renderer.render(parser.parse(s))

        // Then clean it up with jsoup to avoid issues with the pdf generator (and for security)
        Jsoup.clean(html, "", Whitelist.relaxed(), os)
      } catch {
        case ex: Throwable =>
          println(s"Error converting markdown to HTML: $ex: Input was:\n$gfm")
          gfm
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
  def mkTable(head: List[String], rows: List[List[String]]): TypedTag[String] = {
    if (rows.isEmpty) p("n/a")
    else {
      val (newHead, newRows) = compact(head, rows)
      table(
        thead(
          tr(newHead.map(th(_)))
        ),
        tbody(
          for (row <- newRows) yield {
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
      val l = for (r <- rows) yield r(i).length
      l.sum != 0
    }

    val hh = for {
      (h, i) <- head.zipWithIndex
      if notAllEmpty(rows, i)
    } yield (h, i)
    if (hh.length == head.length) {
      (head, rows)
    } else {
      val newHead = hh.map(_._1)
      val indexes = hh.map(_._2)

      def newRow(row: List[String]): List[String] = {
        row.zipWithIndex.filter(p => indexes.contains(p._2)).map(_._1)
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
