import java.io.File

import csw.services.icd.db.IcdDb
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  "Application" should {

    //    "send 404 on a bad request" in new WithApplication{
    //      route(FakeRequest(GET, "/boum")) must beNone
    //    }
    //
    //    "render the index page" in new WithApplication{
    //      val home = route(FakeRequest(GET, "/")).get
    //
    //      status(home) must equalTo(OK)
    //      contentType(home) must beSome.which(_ == "text/html")
    //      contentAsString(home) must contain ("shouts out")
    //    }

    "Get pub/sub info from database" in {
      val db = IcdDb("test")
      db.dropDatabase() // start with a clean db for test

      // ingest examples/NFIRAOS into the DB
      val problems = db.ingest(getTestDir("../examples/NFIRAOS"))
      for (p ← problems) println(p)

      val problems2 = db.ingest(getTestDir("../examples/TCS"))
      for (p ← problems2) println(p)

      val info = controllers.ComponentInfo(db, "NFIRAOS", None, "envCtrl")
      info.compName must equalTo("envCtrl")
      info.publishInfo must not be empty
      info.publishInfo.foreach { pubInfo ⇒
        println(s"envCtrl publishes ${pubInfo.name}")
        pubInfo.subscribers.foreach { subInfo ⇒
          println(s"${subInfo.compName} from ${subInfo.subsystem} subscribes to ${subInfo.name}")
        }
      }
      info.subscribeInfo.foreach { subInfo ⇒
        println(s"envCtrl subscribes to ${subInfo.name} from ${subInfo.subsystem}")
      }
      ok
    }

  }
}
