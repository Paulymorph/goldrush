package goldrush

import cats.effect.concurrent.Ref
import goldrush.Licenser.Licenser
import monix.eval.Task
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class LicenserSpec extends AsyncFlatSpec with MonixSpec with Matchers {

  "Licenser" should "issue no more than limited licenses" in {
    val limit = 10
    testLicenser(limit, 1)(_ => Task.unit) { (_, issuedCounter) =>
      issuedCounter.get.map(_ shouldBe <=(limit))
    }
  }

  it should "issue new licenses mildly when used" in {
    val limit = 10
    val licenseUses = limit * 10
    testLicenser(limit, 1) { licenseGenerator =>
      Task.parTraverseUnordered(0 until licenseUses) { _ =>
        licenseGenerator.use(l => Task.delay(println(s"Used $l")))
      }
    } { (_, issuedCounter) =>
      issuedCounter.get.map(_ shouldBe <=(limit + licenseUses))
    }
  }

  it should "issue <= limit + (uses / allows)" in {
    val limit = 1
    val licenseUses = limit * 10
    val licenseAllows = 3
    testLicenser(limit, licenseAllows) { licenseGenerator =>
      Task.parTraverseUnordered(0 until licenseUses) { _ =>
        licenseGenerator.use(l => Task.delay(println(s"Used $l")))
      }
    } { (_, issuedCounter) =>
      issuedCounter.get.map(_ shouldBe <=(limit + licenseUses / licenseAllows + 1))
    }
  }

  private def testLicenser[A](
      limit: Int,
      licenseAllows: Int
  )(useLicenser: Licenser[Task] => Task[A])(test: (A, Ref[Task, Int]) => Task[Assertion]) = {
    for {
      issuesCounter <- Ref.of[Task, Int](0)
      issueLicense = issuesCounter
        .updateAndGet(_ + 1)
        .map(id => License(id, licenseAllows, 0))
      useResult <- Licenser(limit, issueLicense).use(useLicenser)
      tested <- test(useResult, issuesCounter)
    } yield tested
  }
}
