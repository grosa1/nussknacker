package pl.touk.nussknacker.engine.lite

import cats.{Id, ~>}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessName, Source, SourceTestSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestRecord, TestRecord}
import pl.touk.nussknacker.engine.api.{JobData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.higherKinds

trait TestRunner {
  def runTest[T](modelData: ModelData,
                 scenarioTestData: ScenarioTestData,
                 process: CanonicalProcess,
                 variableEncoder: Any => T): TestResults[T]
}

//TODO: integrate with Engine somehow?
class InterpreterTestRunner[F[_] : InterpreterShape : CapabilityTransformer : EffectUnwrapper, Input, Res <: AnyRef] extends TestRunner {

  def runTest[T](modelData: ModelData,
                 scenarioTestData: ScenarioTestData,
                 process: CanonicalProcess,
                 variableEncoder: Any => T): TestResults[T] = {

    //TODO: probably we don't need statics here, we don't serialize stuff like in Flink
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)

    //in tests we don't send metrics anywhere
    val testContext = LiteEngineRuntimeContextPreparer.noOp.prepare(testJobData(process))
    val componentUseCase: ComponentUseCase = ComponentUseCase.TestRuntime

    //FIXME: validation??
    val scenarioInterpreter = ScenarioInterpreterFactory.createInterpreter[F, Input, Res](process, modelData,
      additionalListeners = List(collectingListener), new TestServiceInvocationCollector(collectingListener.runId), componentUseCase
    )(SynchronousExecutionContext.ctx, implicitly[InterpreterShape[F]], implicitly[CapabilityTransformer[F]]) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    val inputs = ScenarioInputBatch(scenarioTestData.testRecords.map { case ScenarioTestRecord(NodeId(sourceIdValue), testRecord) =>
      val sourceId = SourceId(sourceIdValue)
      val source = scenarioInterpreter.sources.getOrElse(sourceId,
        throw new IllegalArgumentException(s"Found source '$sourceIdValue' in a test record but is not present in the scenario"))
      sourceId -> prepareRecordForTest[Input](source, testRecord)
    })

    try {
      scenarioInterpreter.open(testContext)

      val results = implicitly[EffectUnwrapper[F]].apply(scenarioInterpreter.invoke(inputs))

      collectSinkResults(collectingListener.runId, results)
      collectingListener.results
    } finally {
      collectingListener.clean()
      scenarioInterpreter.close()
      testContext.close()
    }

  }

  private def testJobData(process: CanonicalProcess) = {
    // testing process may be unreleased, so it has no version
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version"))
    val deploymentData = DeploymentData.empty
    JobData(process.metaData, processVersion)
  }

  private def prepareRecordForTest[T](source: Source, testRecord: TestRecord): T = {
    val sourceTestSupport = source match {
      case e: SourceTestSupport[T@unchecked] => e
      case other => throw new IllegalArgumentException(s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser")
    }
    sourceTestSupport.testRecordParser.parse(testRecord)
  }

  private def collectSinkResults(runId: TestRunId, results: ResultType[EndResult[Res]]): Unit = {
    val successfulResults = results.value
    successfulResults.foreach { result =>
      val node = result.nodeId.id
      SinkInvocationCollector(runId, node, node).collect(result.context, result.result)
    }
  }

}

object TestRunner {

  type EffectUnwrapper[F[_]] = F ~> Id

  private val scenarioTimeout = 10 seconds

  //TODO: should we consider configurable timeout?
  implicit val unwrapper: EffectUnwrapper[Future] = new EffectUnwrapper[Future] {
    override def apply[Y](eff: Future[Y]): Y = Await.result(eff, scenarioTimeout)
  }

}
