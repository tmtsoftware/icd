package csw.services.icd.viz;

import java.io.File

import csw.services.icd.db.IcdDbDefaults.{defaultDbName, defaultHost, defaultPort}
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import icd.web.shared.SubsystemWithVersion

/**
 * Command line options ("icd-viz --help" prints a usage message with descriptions of all the options)
 */
case class IcdVizOptions(
    dbName: String = defaultDbName,
    host: String = defaultHost,
    port: Int = defaultPort,
    // primary components with subsystem and optional version
    components: List[SubsystemWithVersion] = Nil,
    // primary subsystems with optional versions
    subsystems: List[SubsystemWithVersion] = Nil,
    // Display plot in a window
    showPlot: Boolean = true,
    // Write image to file
    imageFile: Option[File] = None,
    // Write dot source to file
    dotFile: Option[File] = None,
    // Image aspect ratio (y/x)
    ratio: Double = 0.5,
    // Plot missing events
    missingEvents: Boolean = true,
    // Plot missing commands
    missingCommands: Boolean = false,
    // Plot command labels
    commandLabels: Boolean = false,
    // Plot event labels
    eventLabels: Boolean = true,
    // Group components from same subsystem together
    groupSubsystems: Boolean = true,
    // Dot layout engine: One of {dot,fdp,sfdp,twopi,neato,circo,patchwork}
    layout: String = "dot",
    // Node overlap handling: {true,false,scale}
    overlap: String = "scale",
    // Use splines for edges?
    splines: Boolean = true,
    // list of component types (HCD,Assembly,Sequencer,Application) to omit as primaries (default={'HCD'})
    omitTypes: List[String] = List("HCD")
)
