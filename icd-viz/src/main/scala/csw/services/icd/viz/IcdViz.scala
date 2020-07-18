package csw.services.icd.viz

import java.io.File

import csw.services.icd.db.IcdDb
import icd.web.shared.{BuildInfo, SubsystemWithVersion}

//noinspection DuplicatedCode
object IcdViz extends App {

  // Parser for the command line options
  private val parser = new scopt.OptionParser[IcdVizOptions]("icd-viz") {
    head("icd-viz", BuildInfo.version)

    opt[String]('d', "db") valueName "<name>" action { (x, c) =>
      c.copy(dbName = x)
    } text "The name of the database to use (default: icds)"

    opt[String]('h', "host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = x)
    } text "The host name where the database is running (default: localhost)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text "The port number to use for the database (default: 27017)"

    opt[String]("components") valueName "prefix1[:version],prefix2[:version],..." action { (x, c) =>
      c.copy(components = x.split(',').toList.map(SubsystemWithVersion.apply))
    } text "Comma-separated list of primary component prefixes with optional versions (:version)"

    opt[String]("subsystems") valueName "subsystem1[:version],subsystem2[:version],..." action { (x, c) =>
      c.copy(subsystems = x.split(',').toList.map(SubsystemWithVersion.apply))
    } text "Comma-separated list of primary subsystems with optional versions (:version)"

    opt[Boolean]("showplot") action { (x, c) =>
      c.copy(showPlot = x)
    } text "Display plot in a window (default=True)"

    opt[File]('o', "imagefile") valueName "<file>" action { (x, c) =>
      c.copy(imageFile = Some(x))
    } text "Write image to file (default=None)"

    opt[File]("dotfile") valueName "<file>" action { (x, c) =>
      c.copy(dotFile = Some(x))
    } text "Write dot source to file (default=None)"

    opt[Double]("ratio") valueName "<ratio>" action { (x, c) =>
      c.copy(ratio = x)
    } text "Image aspect ratio (y/x) (default=0.5)"

    opt[Boolean]("missingevents") action { (x, c) =>
      c.copy(missingEvents = x)
    } text "Plot missing events (default=True)"

    opt[Boolean]("missingcommands") action { (x, c) =>
      c.copy(missingCommands = x)
    } text "Plot missing commands (default=True)"

    opt[Boolean]("commandlabels") action { (x, c) =>
      c.copy(commandLabels = x)
    } text "Plot command labels (default=False)"

    opt[Boolean]("eventlabels") action { (x, c) =>
      c.copy(eventLabels = x)
    } text "Plot event labels (default=True)"

    opt[Boolean]("groupsubsystems") action { (x, c) =>
      c.copy(groupSubsystems = x)
    } text "Group components from same subsystem together (default=True)"

    opt[String]("layout") valueName "one of {dot,fdp,sfdp,twopi,neato,circo,patchwork}" action { (x, c) =>
      c.copy(layout = x)
    } text "Dot layout engine (default=dot)"

    opt[String]("overlap") valueName "one of {true,false,scale}" action { (x, c) =>
      c.copy(overlap = x)
    } text "Node overlap handling (default=scale)"

    opt[Boolean]("splines") action { (x, c) =>
      c.copy(splines = x)
    } text "Use splines for edges? (default=True)"

    opt[String]("omittypes") action { (x, c) =>
      c.copy(omitTypes = x.split(',').toList)
    } text "Comma-separated list of component types (HCD,Assembly,Sequencer,Application) to omit as primaries (default={'HCD'})"

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
    val db = IcdDb(options.dbName, options.host, options.port)
    IcdVizManager.showRelationships(db, options)
    System.exit(0)
  }

}
