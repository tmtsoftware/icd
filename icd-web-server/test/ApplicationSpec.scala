//import org.junit.runner.*
//import org.specs2.runner.*
//import play.api.test.*
//
///**
// * Add your spec here.
// * You can mock out a whole application including requests, plugins etc.
// * For more information, consult the wiki.
// */
//@RunWith(classOf[JUnitRunner])
//class ApplicationSpec() extends PlaySpecification {
//
//  "Application" should {
//
//    "send 404 on a bad request" in new WithDepsApplication {
//      route(app, FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
//    }
//
//    "render the index page" in new WithDepsApplication {
//      val home = route(app, FakeRequest(GET, "/")).get
//
//      status(home) must equalTo(OK)
//      contentType(home) must beSome.which(_ == "text/html")
//      //      contentAsString(home) must contain ("shouts out")
//    }
//  }
//}
