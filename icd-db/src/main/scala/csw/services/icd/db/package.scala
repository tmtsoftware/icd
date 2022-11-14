package csw.services.icd

import java.io.File
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
}
