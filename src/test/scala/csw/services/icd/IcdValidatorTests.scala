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

  val publishSchema = ConfigFactory.parseResources("publish-schema.conf").resolve(ConfigResolveOptions.noSystem())
  val publishGood1 = ConfigFactory.parseResources("publish-good1.conf").resolve(ConfigResolveOptions.noSystem())
  val publishBad1 = ConfigFactory.parseResources("publish-bad1.conf").resolve(ConfigResolveOptions.noSystem())

  def printResult(result: List[Problem]): Unit = {
    for (problem ← result) {
      println(s"${problem.severity}: ${problem.message}\n")
      println(problem.json)
    }
  }

  def checkResult(result: List[Problem]): Unit = {
    if (result.nonEmpty) {
      printResult(result)
      fail("Validation failed")
    }
  }

  test("Test publish ICD validation") {
    // test valid files
    checkResult(validate(icdGood1, icdSchema))
    checkResult(validate(publishGood1, publishSchema))

    // test files with errors
    val problems1 = validate(icdBad1, icdSchema)
    assert(problems1.length != 0)

    val problems2 = validate(publishBad1, publishSchema)
    assert(problems2.length != 0)
  }
}
