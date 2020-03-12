package csw.services.icd

package object db {
  // Make a prefix from a component
  def makePrefix(subsystem: String, component: String): String =  s"$subsystem.$component"
}
