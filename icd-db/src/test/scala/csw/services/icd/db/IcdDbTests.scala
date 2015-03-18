package csw.services.icd.db

import java.io.File

import com.typesafe.config.{ ConfigRenderOptions, ConfigFactory }
import org.scalatest.{ DoNotDiscover, FunSuite }

/**
 * Tests the IcdDb class (Note: Assumes MongoDB is running)
 */
@DoNotDiscover
class IcdDbTests extends FunSuite {

  test("Ingest example ICD into database") {
    import collection.JavaConversions._
    val db = IcdDb("test")
    db.dropDatabase()
    val problems = db.ingest("NFIRAOS", new File("examples/NFIRAOS"))
    for (p ← problems) println(p)
    assert(problems.isEmpty)

    // XXX
    val coll = db.db("NFIRAOS.envCtrl.publish")
    assert(coll.count() == 1)

    val config = ConfigFactory.parseString(coll.head.toString)
    val jsonOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
    println(config.root.render(jsonOptions))
  }
}
