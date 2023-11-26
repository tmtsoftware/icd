package csw.services.icd.viz

import java.io.File

import csw.services.icd.db.IcdDb
import icd.web.shared.{BuildInfo, IcdVizOptions, SubsystemWithVersion}

//noinspection DuplicatedCode
object IcdViz extends App {

  // Parser for the command line options
  private val parser = new scopt.OptionParser[IcdVizOptions]("icd-viz") {
    import IcdVizOptions._
    import csw.services.icd.db.IcdDbDefaults.{defaultDbName, defaultHost, defaultPort}
    head("icd-viz", BuildInfo.version)

    opt[String]('d', "db") valueName "<name>" action { (x, c) =>
      c.copy(dbName = Some(x))
    } text s"The name of the database to use (default: $defaultDbName)"

    opt[String]('h', "host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = Some(x))
    } text s"The host name where the database is running (default: $defaultHost)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = Some(x))
    } text s"The port number to use for the database (default: $defaultPort)"

    opt[String]("components") valueName "prefix1[:version],prefix2[:version],..." action { (x, c) =>
      c.copy(components = x.split(',').toList.map(SubsystemWithVersion.apply))
    } text "Comma-separated list of primary component prefixes with optional versions (:version)"

    opt[String]("subsystems") valueName "subsystem1[:version],subsystem2[:version],..." action { (x, c) =>
      c.copy(subsystems = x.split(',').toList.map(SubsystemWithVersion.apply))
    } text "Comma-separated list of primary subsystems with optional versions (:version)"

    opt[Boolean]("showplot") action { (x, c) =>
      c.copy(showPlot = x)
    } text s"Display plot in a window (default=$defaultShowPlot)"

    opt[File]('o', "imagefile") valueName "<file>" action { (x, c) =>
      c.copy(imageFile = Some(x))
    } text s"Write image to file in format based on file suffix (default=None, formats: ${imageFormats.mkString(", ")})"

    opt[File]("dotfile") valueName "<file>" action { (x, c) =>
      c.copy(dotFile = Some(x))
    } text "Write dot source to file (default=None)"

    opt[Double]("ratio") valueName "<ratio>" action { (x, c) =>
      c.copy(ratio = x)
    } text s"Image aspect ratio (y/x) (default=$defaultRatio)"

    opt[Boolean]("missingevents") action { (x, c) =>
      c.copy(missingEvents = x)
    } text s"Plot missing events (default=$defaultMissingEvents)"

    opt[Boolean]("missingcommands") action { (x, c) =>
      c.copy(missingCommands = x)
    } text s"Plot missing commands (default=$defaultMissingCommands)"

    opt[Boolean]("commandlabels") action { (x, c) =>
      c.copy(commandLabels = x)
    } text s"Plot command labels (default=$defaultCommandLabels)"

    opt[Boolean]("eventlabels") action { (x, c) =>
      c.copy(eventLabels = x)
    } text s"Plot event labels (default=$defaultEventLabels)"

    opt[Boolean]("groupsubsystems") action { (x, c) =>
      c.copy(groupSubsystems = x)
    } text s"Group components from same subsystem together (default=$defaultGroupSubsystems)"

    opt[Boolean]("onlysubsystems") action { (x, c) =>
      c.copy(onlySubsystems = x, groupSubsystems = false)
    } text s"Only display subsystems, not components (implies --groupsubsystems false, default=$defaultOnlySubsystems)"

    opt[String]("layout") valueName s"one of: ${graphLayouts.mkString(", ")}" action { (x, c) =>
      c.copy(layout = x)
    } text s"Dot layout engine (default=$defaultLayout)"

    opt[String]("overlap") valueName s"one of ${overlapValues.mkString(", ")}" action { (x, c) =>
      c.copy(overlap = x)
    } text s"Node overlap handling (default=$defaultOverlap)"

    opt[Boolean]("splines") action { (x, c) =>
      c.copy(splines = x)
    } text s"Use splines for edges? (default=$defaultUseSplines)"

    opt[String]("omittypes") action { (x, c) =>
      c.copy(omitTypes = x.split(',').toList .filter(_ != "None"))
    } text s"Comma-separated list of component types (${allowedOmitTypes.mkString(", ")}) to omit as primaries (default='$defaultOmit')"

    opt[String]("imageformat") action { (x, c) =>
      c.copy(imageFormat = x)
    } text s"Image format (Used only if imageFile not given or has invalid suffix). One of {${imageFormats.mkString(", ")}} (default='$defaultImageFormat')"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, IcdVizOptions()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  // Run the application
  private def run(options: IcdVizOptions): Unit = {
    import csw.services.icd.db.IcdDbDefaults.{defaultDbName, defaultHost, defaultPort}
    val db = IcdDb(
      options.dbName.getOrElse(defaultDbName),
      options.host.getOrElse(defaultHost),
      options.port.getOrElse(defaultPort))
    IcdVizManager.showRelationships(db, options)
    System.exit(0)
  }

}
