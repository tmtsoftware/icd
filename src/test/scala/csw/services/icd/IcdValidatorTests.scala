package csw.services.icd

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory}
import csw.services.icd.IcdValidator._
import org.scalatest.FunSuite

/**
 *
 */
class IcdValidatorTests extends FunSuite {

  val publishSchema = ConfigFactory.parseResources("publish-schema.conf").resolve(ConfigResolveOptions.noSystem())
  val publishGood1 = ConfigFactory.parseResources("publish-good1.conf").resolve(ConfigResolveOptions.noSystem())
  val publishBad1 = ConfigFactory.parseResources("publish-bad1.conf").resolve(ConfigResolveOptions.noSystem())

  def checkResult(result: List[Problem]): Unit = {
    if (result.nonEmpty) {
      for (problem ← result) {
        println(s"${problem.severity}: ${problem.message}\n")
        println(problem.json)
      }
      fail("Validation failed")
    }
  }

  test("Test publish ICD validation") {
    checkResult(validate(publishGood1, publishSchema))

//    val problems = validate(publishBad1, publishSchema)
//    assert(problems.length == 1)
//    for (problem ← problems) {
//      println(s"${problem.severity}: ${problem.message}\n")
//      println(problem.json)
//    }
  }
}
