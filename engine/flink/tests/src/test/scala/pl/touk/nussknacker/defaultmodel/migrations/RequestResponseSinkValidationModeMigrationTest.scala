package pl.touk.nussknacker.defaultmodel.migrations

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node.Sink
import pl.touk.nussknacker.ui.api.helpers.TestCategories

import scala.reflect.ClassTag

class RequestResponseSinkValidationModeMigrationTest extends AnyFunSuite {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("should migrate union node 'value' parameter name to Output expression") {

    val process = ScenarioBuilder
        .requestResponse("test")
        .source("source", "request")
        .emptySink("sink", "response")

    val results = RequestResponseSinkValidationModeMigration.migrateProcess(process, TestCategories.Category1)
    getFirst[Sink](results).parameters shouldBe List(Parameter("Value validation mode", "'lax'"))
  }

  private def getFirst[T: ClassTag](scenario: CanonicalProcess): T = scenario.collectAllNodes.collectFirst { case t: T => t }.get
}
