package csw.services.icd.gfm

/**
 * Base trait for converting model class to Gfm
 * (Github flavored markdown: https://help.github.com/articles/github-flavored-markdown/)
 */
trait Gfm {

  val gfm: String

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
}
