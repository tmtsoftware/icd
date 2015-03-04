package csw.services.icd.gfm

/**
 * Base trait for converting model class to Gfm
 * (Github flavored markdown: https://help.github.com/articles/github-flavored-markdown/)
 */
trait Gfm {
  /**
   * Returns a string in GFM markdown format
   */
  val gfm: String
}

// GFM utils
object Gfm {
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

  def mkParagraph(text: String) = s"${paragraphFilter(text)}\n"

  /**
   * Returns a markdown table with the given column headings and list of rows
   */
  def mkTable(head: List[String], rows: List[List[String]]): String = {
    if (rows.isEmpty) "n/a"
    else {
      val (newHead, newRows) = compact(head, rows)
      val hs = strip(newHead).mkString(" | ")
      val sep = "---|" * newHead.size
      val rs = newRows.map(strip(_).mkString(" | ")).mkString(" |\n")
      s"$hs\n$sep\n$rs |\n\n"
    }
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
   * Returns a markdown formatted list of items
   */
  def mkList(list: List[String]): String = (for (item ← list) yield s"* $item").mkString("\n") + "\n\n"

  // Strips "|" and "\n" from the given string (since that would break the GFM table format)
  private def tableFilter(s: String): String = {
    if (s.contains("|") || s.contains("\n")) {
      println(s"Warning: text appearing in tables may not contain newlines or '|', at: '$s'")
      s.filter(c ⇒ c != '|' && c != '\n')
    } else s
  }

  // Strips leading whitespace from each line of text
  private def paragraphFilter(s: String): String =
    (for (line ← s.split("\n")) yield line.trim).mkString("\n")

  // Strip special markdown table chars ("|" and "\n") from strings in list
  private def strip(list: List[String]): List[String] = list.map(tableFilter)
}
