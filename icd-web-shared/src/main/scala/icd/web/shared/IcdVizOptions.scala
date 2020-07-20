package icd.web.shared

import java.io.File
import IcdVizOptions._

object IcdVizOptions {
  // Allowed/default values for options
  val allowedOmitTypes       = List("HCD", "Assembly", "Sequencer", "Application")
  val defaultOmit            = "HCD"
  val graphLayouts           = List("dot", "fdp", "sfdp", "twopi", "neato", "circo", "patchwork")
  val defaultLayout          = "dot"
  val imageFormats           = List("PDF", "PNG", "SVG", "EPS")
  val defaultImageFormat     = "PDF"
  val overlapValues          = List("true", "false", "scale")
  val defaultOverlap         = "scale"
  val defaultShowPlot        = true
  val defaultRatio           = 0.5
  val defaultMissingEvents   = true
  val defaultMissingCommands = false
  val defaultEventLabels     = true
  val defaultCommandLabels   = false
  val defaultGroupSubsystems = true
  val defaultUseSplines      = true
}

/**
 * Command line options ("icd-viz --help" prints a usage message with descriptions of all the options)
 */
case class IcdVizOptions(
    // Optionally override database name, host, port
    dbName: Option[String] = None,
    host: Option[String] = None,
    port: Option[Int] = None,
    // primary components with subsystem and optional version
    components: List[SubsystemWithVersion] = Nil,
    // primary subsystems with optional versions
    subsystems: List[SubsystemWithVersion] = Nil,
    // Display plot in a window
    showPlot: Boolean = defaultShowPlot,
    // Write image to file
    imageFile: Option[File] = None,
    // Write dot source to file
    dotFile: Option[File] = None,
    // Image aspect ratio (y/x)
    ratio: Double = defaultRatio,
    // Plot missing events
    missingEvents: Boolean = defaultMissingEvents,
    // Plot missing commands
    missingCommands: Boolean = defaultMissingCommands,
    // Plot command labels
    commandLabels: Boolean = defaultCommandLabels,
    // Plot event labels
    eventLabels: Boolean = defaultEventLabels,
    // Group components from same subsystem together
    groupSubsystems: Boolean = defaultGroupSubsystems,
    // Dot layout engine: One of {dot,fdp,sfdp,twopi,neato,circo,patchwork}
    layout: String = defaultLayout,
    // Node overlap handling: {true,false,scale}
    overlap: String = defaultOverlap,
    // Use splines for edges?
    splines: Boolean = defaultUseSplines,
    // list of component types (HCD,Assembly,Sequencer,Application) to omit as primaries (default={'HCD'})
    omitTypes: List[String] = List(defaultOmit)
)
