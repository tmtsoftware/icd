package csw.services.icd

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory}
import csw.services.icd.IcdValidator._
import org.scalatest.FunSuite

/**
 *
 */
class IcdValidatorTests extends FunSuite {

  val icdSchema = ConfigFactory.parseResources("icd-schema.conf").resolve(ConfigResolveOptions.noSystem())
  val icdGood1 = ConfigFactory.parseResources("icd-good1.conf").resolve(ConfigResolveOptions.noSystem())
  val icdBad1 = ConfigFactory.parseResources("icd-bad1.conf").resolve(ConfigResolveOptions.noSystem())

  def checkResult(result: List[Problem]): Unit = {
    if (result.nonEmpty) {
      for (problem ← result) {
        println(s"${problem.severity}: ${problem.message}\n")
        println(problem.json)
      }
      fail("Validation failed")
    }
  }

  test("Test ICD validation") {
    checkResult(validate(icdGood1, icdSchema))

//    val problems = validate(icdBad1, icdSchema)
//    assert(problems.length == 1)
//    for (problem ← problems) {
//      println(s"${problem.severity}: ${problem.message}")
//    }
  }
}
