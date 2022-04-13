package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, WithCategories}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global

class RequestResponseTestScenarioRunner(val components: Map[String, WithCategories[Component]], val config: Config) extends TestScenarioRunner {

  override def runWithData[T: ClassTag, Result](scenario: EspProcess, data: List[T]): List[Result] = {
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    val interpreter = RequestResponseInterpreter[Future](scenario,
      ProcessVersion.empty, LiteEngineRuntimeContextPreparer.noOp, modelData, Nil, null, null).getOrElse(throw new IllegalArgumentException(""))
    Future.sequence(data.map(interpreter.invokeToOutput)).futureValue.map(_.asInstanceOf[Result])
  }
}
