package csw.services.icd

import java.io.File
import java.nio.file.Paths
import scala.io.Source

package object db {
//  // Make a prefix from a component
//  def makePrefix(subsystem: String, component: String): String = s"$subsystem.$component"

  // Gets the contents of the file as a String
  def getFileContents(path: String): String = {
    val source = Source.fromFile(path)
    val s      = source.mkString
    source.close()
    s
  }

  // Gets the contents of the file as a String
  def getFileContents(file: File): String = {
    val source = Source.fromFile(file)
    val s      = source.mkString
    source.close()
    s
  }

  // Remove the first two dirs from a path (for example: /tmp/xxx/dir/file becomes dir/file)
  def removeTmpDirFromFile(file: File): String = {
    val p = file.toPath
    p.subpath(2, p.getNameCount).toString
  }

  def firstParagraph(s: String): String = {
    val i = s.indexOf("</p>")
    if (i == -1) s else s.substring(0, i + 4)
  }

  def firstParagraphPlainText(s: String): String = {
    val s2 = firstParagraph(s)
    if (s2.startsWith("<p>") && s2.endsWith("</p>"))
      s2.substring(3, s2.length - 4)
    else
      s2
  }
}
