package csw.services.icd.gfm

/**
 * Base trait for converting model class to Gfm
 * (Github flavored markdown: https://help.github.com/articles/github-flavored-markdown/)
 */
trait Gfm {

  val gfm: String

  /**
   * Returns a numbered markdown heading with the given level and depth
   */
  def mkHeading(level: Level, depth: Int, name: String): String = {
    s"\n${"#" * (depth + 1)}${level(depth)} $name\n"
  }

  /**
   * Returns a markdown heading with the given level
   */
  def mkHeading(depth: Int, name: String): String = {
    s"\n${"#" * (depth + 1)}$name\n"
  }

  def mkParagraph(text: String) = s"$text\n"

  /**
   * Returns a markdown table with the given column headings and list of rows
   */
  def mkTable(head: List[String], rows: List[List[String]]): String = {
    val hs = strip(head).mkString(" | ")
    val sep = "---|" * head.size
    val rs = rows.map(strip(_).mkString(" | ")).mkString(" |\n")
    s"$hs\n$sep\n$rs |\n\n"
  }

  /**
   * Returns a markdown formatted list of items
   */
  def mkList(list: List[String]): String = (for (item ← list) yield s"* $item").mkString("\n") + "\n\n"

  /**
   * Strips "|" and "\n" from the given string (since that would break the GFM table format)
   * @param s string that goes in a GFM table cell
   * @return the new string
   */
  def t(s: String): String = {
    if (s.contains("|") || s.contains("\n")) {
      println(s"Warning: text appearing in tables may not contain newlines or '|', at: '$s'")
      s.filter(c ⇒ c != '|' && c != '\n')
    } else s
  }

  // Strip special markdown table chars ("|" and "\n") from strings
  private def strip(list: List[String]): List[String] = list.map(t)
}
