package pl.touk.nussknacker.engine.util.test

import pl.touk.nussknacker.engine.graph.EspProcess

import scala.reflect.ClassTag

trait TestScenarioRunner {
  def runWithData[T: ClassTag, Result](scenario: EspProcess, data: List[T]): List[Result]
}
