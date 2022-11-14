package csw.services.icd.db

import com.typesafe.config.ConfigFactory
import csw.services.icd.IcdValidator

object Subsystems {
  /**
   * A list of all known TMT subsystems (read from the same resources file used in validating the ICDs)
   */
  val allSubsystems: List[String] = {
    import scala.jdk.CollectionConverters._
    val config = ConfigFactory.parseResources(s"${IcdValidator.currentSchemaVersion}/subsystem.conf")
    config.getStringList("enum").asScala.toList
  }

  /**
   * Compare two subsystems to keep the order defined in subsystem.conf
   */
  def compare(s1: String, s2: String): Int = {
    allSubsystems.indexOf(s1).compare(allSubsystems.indexOf(s2))
  }

  /**
   * Sorts the list of subsystems according to the order in subsystems.conf
   */
  def sorted(subsystems: List[String]): List[String] = {
    subsystems.sortWith((s1, s2) => allSubsystems.indexOf(s1) < allSubsystems.indexOf(s2))
  }
}
