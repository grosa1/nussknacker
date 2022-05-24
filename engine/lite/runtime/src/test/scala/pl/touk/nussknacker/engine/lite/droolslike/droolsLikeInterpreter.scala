package pl.touk.nussknacker.engine.lite.droolslike

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, StateT, ValidatedNel}
import cats.implicits._
import cats.kernel.Monoid
import cats.{Id, Monad}
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedBranchParameter, JoinGenericNodeTransformation, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, ParameterWithExtractor, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ErrorType, ResultType, monoid}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes._
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.{commonTypes, customComponentTypes, interpreterTypes}
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.lite.droolslike.components.JoinerDefinition
import pl.touk.nussknacker.engine.lite.droolslike.droolsLikeInterpreter.{MatchId, MonadState, NodeState, PartialMatches, RecordedMatch, State, Value, matchIdVariable}
import pl.touk.nussknacker.engine.lite.droolslike.model.Event
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import sttp.client.Identity

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.mapAsJavaMapConverter
import scala.language.higherKinds

object model {

  case class Event(id: String, deviceId: String, isMain: Boolean, state: String, version: Long)

}

/*
  to co ponizej obsluguje przypadek: "jest zdarzenie A i zdarzenie B"
  Do wymyslenia, jak obsłużyc przypadek: "jest zdarzenie A i *nie ma* zdarzenia B
  W szczególnosci, co w sytuacji kiedy mamy (A and(1) not B) and(2) C
  i mamy A, jeszcze nie bylo C, w wezle and(2) mamy juz spropagowany czesciowy match z and(1), a
  przychodzi B i match powinien sie wyzerowac... byc moze powinien byc specjalny id w #matchid, ktory
  reprezentuje "sztuczny event" nie-ma-B? i potem odp. to obslugiwac jakos...
*/
object droolsLikeInterpreter {

  type Key = String

  type MatchId = Set[String]

  type Value = AnyRef

  //ukryta zmienna w kontekscie, za pomoca ktorej przekazujemy wiedze na podstawie jakich elementow context powstal
  //(czyli input + dopasowane zdarzenia z joinow)
  val matchIdVariable = "#matchid"

  /*
    tutaj klasy wewnetrzne na stan:
    - nodes - stan dopasowan w poszczegolnych wezlach
    - recordedMatches - jak wrzucamy zdarzenie, to patrzymy do jakis wezlow dochodzi, jesli wczesniej zdarzenie o danym id
      jest w stanie wezla, a teraz do niego nie dochodzi - tzn. ze cos sie zmienilo, trzeba dotychczasowe dopasowanie wyrzucic ze stanu
   */
  case class State(nodes: Map[NodeId, NodeState], recordedMatches: List[RecordedMatch])

  case class NodeState(partialMatches: Map[Key, PartialMatches])

  case class PartialMatches(matches: Map[BranchId, Map[MatchId, Value]], negated: Set[String] = Set.empty)

  case class RecordedMatch(nodeId: NodeId, branchId: BranchId, id: MatchId)

  implicit val shape: InterpreterShape[MonadState] = new InterpreterShape[MonadState] {

    import InterpreterShape._

    override def monad: Monad[MonadState] = implicitly[Monad[MonadState]]

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => MonadState[Either[T, Throwable]] =
      f => StateT.pure(Await.result(transform(f)(ec), 1 second))

  }

  implicit val capabilityTransformer: CapabilityTransformer[MonadState] = new FixedCapabilityTransformer[MonadState]

  type MonadState[T] = StateT[Id, State, T]

}

//tu jest najprostszy join - po kluczu, bez negacji itd.
object components {

  class JoinerDefinition extends CustomStreamTransformer with JoinGenericNodeTransformation[LiteJoinCustomComponent] {

    private val nodeId = TypedNodeDependency[NodeId]

    val KeyParamName = "key"
    val KeyParam: ParameterWithExtractor[Map[String, LazyParameter[CharSequence]]] = ParameterWithExtractor.branchLazyMandatory[CharSequence](KeyParamName)

    val ValueParamName = "value"
    val ValueParam: ParameterWithExtractor[Map[String, LazyParameter[Value]]] = ParameterWithExtractor.branchLazyMandatory[Value](ValueParamName)


    override type State = Nothing

    override def contextTransformation(context: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])
                                      (implicit nodeId: NodeId): NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(KeyParam.parameter :: ValueParam.parameter :: Nil)
      case TransformationStep((KeyParamName, _) :: (ValueParamName, ds: DefinedBranchParameter) :: Nil, _) =>

        val types = ds.expressionByBranchId.mapValues(_.returnType).toList
        val retType = TypedObjectTypingResult(ListMap(types: _*))
        FinalResults(ValidationContext(Map(OutputVariableNameDependency.extract(dependencies) -> retType)))
    }

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): LiteJoinCustomComponent = {
      new Joiner(nodeId.extract(dependencies), KeyParam.extractValue(params), ValueParam.extractValue(params), OutputVariableNameDependency.extract(dependencies))
    }

    override def nodeDependencies: List[NodeDependency] = nodeId :: OutputVariableNameDependency :: Nil
  }

  class Joiner(nodeId: NodeId,
               keyBy: Map[String, LazyParameter[CharSequence]],
               value: Map[String, LazyParameter[Value]],
               output: String) extends LiteJoinCustomComponent {


    override def createTransformation[F[_] : Monad, Result](continuation: commonTypes.DataBatch => F[ResultType[Result]], context: customComponentTypes.CustomComponentContext[F]): customComponentTypes.JoinDataBatch => F[ResultType[Result]] = {
      val singleTransformation = createSingleTransformation(continuation, context)
      batch => Monoid.combineAll(batch.value.map(singleTransformation.tupled))
    }

    private def createSingleTransformation[F[_] : Monad, Result](continuation: commonTypes.DataBatch => F[ResultType[Result]],
                                                                 context: customComponentTypes.CustomComponentContext[F]): (BranchId, Context) => F[ResultType[Result]] = {
      val keyByBranchId = keyBy.mapValues(context.interpreter.syncInterpretationFunction)
      val valueByBranchId = value.mapValues(context.interpreter.syncInterpretationFunction)
      val branchNames = keyBy.keys.toList.map(BranchId)


      context.capabilityTransformer.transform[MonadState].map { transformer =>
        (branchId: BranchId, ctx: Context) => {
          transformer.apply {
            StateT.apply[Identity, droolsLikeInterpreter.State, DataBatch] { st =>
              val key = keyByBranchId(branchId.value)(ctx).toString
              val value = valueByBranchId(branchId.value)(ctx)
              val (newState, actions) = invoke(branchNames, st, branchId, key, value, ctx)
              (newState, actions)
            }
          }.flatMap(continuation)
        }
      }.getOrElse(throw new IllegalArgumentException("Failed..."))


    }

    private def invoke(branchNames: List[BranchId], state: droolsLikeInterpreter.State, branchId: BranchId, key: String, value: Value, ctx: Context): (droolsLikeInterpreter.State, DataBatch) = {
      val ids: MatchId = ctx.apply[MatchId](matchIdVariable)
      val partialMatches = state.nodes.getOrElse(nodeId, NodeState(Map.empty)).partialMatches
      val partialsForKey: PartialMatches = partialMatches.getOrElse(key, PartialMatches(branchNames.map(_ -> Map.empty[MatchId, Value]).toMap))
      val noChangeMade = partialsForKey.matches(branchId).get(ids).contains(value)
      val (newPartials, batchToFire) = if (noChangeMade) {
        (partialsForKey, Nil)
      } else {
        prepareBatch(branchId, ids, value, partialsForKey)
      }
      val recorded = RecordedMatch(nodeId, branchId, ids)
      (State(nodes = state.nodes + (nodeId -> NodeState(partialMatches + (key -> newPartials))),
        recordedMatches = recorded :: state.recordedMatches), DataBatch(batchToFire))
    }

    private def prepareBatch(branchId: BranchId, ids: MatchId, value: Value, partials: PartialMatches): (PartialMatches, List[Context]) = {
      val matchesToGenerate = partials.matches + (branchId -> Map(ids -> value))

      @tailrec
      def addBranches(toGenerate: List[(BranchId, Map[MatchId, Value])], acc: List[Map[BranchId, (MatchId, Value)]]): List[Map[BranchId, (MatchId, Value)]] = {
        toGenerate match {
          case Nil => acc
          case (branchId, matchesForBranch) :: tail =>
            val newAcc = acc.flatMap { map =>
              matchesForBranch.toList.map(v => map + (branchId -> v))
            }
            addBranches(tail, newAcc)
        }
      }
      val list = matchesToGenerate.toList
      val generated = addBranches(list.tail, matchesToGenerate.head._2.map { case (mid, v) =>
        Map(matchesToGenerate.head._1 -> (mid, v))
      }.toList)

      val ctxs: List[Context] = generated.map { branchToVals =>
        Context("").withVariables(Map(
          output -> branchToVals.map { case (k, (_, value)) => k.value -> value }.asJava,
          matchIdVariable -> branchToVals.values.flatMap(_._1).toSet
        ))
      }
      val newMatchesForBranch = partials.matches.getOrElse(branchId, Map.empty) + (ids -> value)
      val newMatches = partials.copy(matches = partials.matches + (branchId -> newMatchesForBranch))
      (newMatches, ctxs)
    }

  }

}

object sample {

  object SimpleConfigCreator extends EmptyProcessConfigCreator {

    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[JoinerDefinition]] =
      Map("join" -> WithCategories(new JoinerDefinition))

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map("source" -> WithCategories(SimpleSourceFactory))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("sink" -> WithCategories(SimpleSinkFactory))

  }

  object SimpleSourceFactory extends SourceFactory {
    @MethodToInvoke
    def create(): Source = new LiteSource[Event] with ReturningType {
      override def createTransformation[F[_] : Monad](evaluateLazyParameter: CustomComponentContext[F]): Event => ValidatedNel[ErrorType, Context] =
        input => Valid(Context(s"${input.id}-${input.version}", Map("input" -> input, matchIdVariable -> Set(input.id)), None))

      override def returnType: typing.TypingResult = Typed[Event]
    }
  }

  object SimpleSinkFactory extends SinkFactory {
    @MethodToInvoke
    def create(@ParamName("value") value: LazyParameter[AnyRef]): LazyParamSink[AnyRef] = (_: LazyParameterInterpreter) => value
  }

  val modelData: LocalModelData = LocalModelData(ConfigFactory.empty(), SimpleConfigCreator)

  import droolsLikeInterpreter.capabilityTransformer
  import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

  def scenarioInterpreter(process: EspProcess): ScenarioInterpreterWithLifecycle[MonadState, Event, AnyRef] =
    ScenarioInterpreterFactory.createInterpreter[MonadState, Event, AnyRef](process, modelData).fold(
      er => throw new IllegalArgumentException(er.toString()),
      identity
    )

}

object test extends App {

  val JoinNodeId = "joinId"

  val Branch1 = "branch1"

  val Branch2 = "branch2"

  val scenario = EspProcess(MetaData("test", LiteStreamMetaData()), NonEmptyList.of[SourceNode](
    GraphBuilder.source("main", "source")
      .filter("mainfilter", "#input.isMain and #input.state == 'RUN'")
      .branchEnd(Branch1, JoinNodeId),
    GraphBuilder.source("nomain", "source")
      .filter("nomainfilter", "!#input.isMain and #input.state == 'RUN'")
      .branchEnd(Branch2, JoinNodeId),
    GraphBuilder
      .join(JoinNodeId, "join", Some("outputVar"),
        List(
          Branch1 -> List(
            "value" -> "#input",
            "key" -> s"#input.deviceId + '-suf'"
          ),
          Branch2 -> List(
            "value" -> "#input",
            "key" -> "#input.deviceId + '-suf'"
          )
        )
      )
      .emptySink("sink", "sink", "value" -> "#outputVar")
  ))

  val interpreter = sample.scenarioInterpreter(scenario)
  interpreter.open(LiteEngineRuntimeContextPreparer.noOp.prepare(JobData(scenario.metaData, ProcessVersion.empty)))

  //tu jest glowna petla, dla kazdego zdarzenia odpalamy interpreter, potem
  //usuwamy ze stanu te czesciowe dopasowania, ktore powstaly na podstawie danego eventu, a ktore nie zostaly dopasowane obecnie
  private def invoke(events: List[Event]): List[ResultType[EndResult[AnyRef]]] = {
    events.foldLeft((Map.empty[NodeId, NodeState], List.empty[ResultType[EndResult[AnyRef]]])) {
      case ((state, actions), event) =>
        val (newState, newAction) = invoke(event, state)
        (newState, newAction :: actions)
    }._2
  }

  private def invoke(event: Event, state: Map[NodeId, NodeState]): (Map[NodeId, NodeState], ResultType[EndResult[AnyRef]]) = {
    val inputBatch = ScenarioInputBatch(interpreter.sources.keys.map(sid => (sid, event)).toList)

    val result: MonadState[ResultType[interpreterTypes.EndResult[AnyRef]]] = interpreter.invoke(inputBatch)
    val (State(nodeState, recorded), actions) = result.run(State(state, Nil))
    val newState = nodeState.map { case (nodeId, matches) =>
      val recordedForNode = recorded.filter(_.nodeId == nodeId)
      val newMatches = matches.partialMatches.mapValuesNow { case PartialMatches(matches, n) =>
        PartialMatches(matches.map { case (bid, matchesForBranch) =>
          val recordedForBranch = recordedForNode.filter(_.branchId == bid)
          (bid, matchesForBranch.filter {
            case (mid, _) => !mid.contains(event.id) || recordedForBranch.exists(_.id == mid)
          })
        }, n)
      }
      (nodeId, NodeState(newMatches))
    }
    (newState, actions)
  }

  val res = invoke(List(
    Event("event1", "device1", true, "RUN", 1),
    Event("event2", "device2", true, "RUN", 1),
    Event("event2", "device2", true, "CLEAR", 2),
    Event("event3", "device2", false, "RUN", 1),
    Event("event4", "device1", false, "RUN", 1)

  ))
  println(res.map(_.run._2.map(_.result)))

  interpreter.close()

}