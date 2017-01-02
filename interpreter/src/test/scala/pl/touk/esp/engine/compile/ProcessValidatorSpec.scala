package pl.touk.esp.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.api.lazyy.ContextWithLazyValuesProvider
import pl.touk.esp.engine.api.process.WithCategories
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.esp.engine.graph.node.Case
import pl.touk.esp.engine.types.EspTypeUtils

import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  private val baseDefinition = ProcessDefinition(
    Map("sampleEnricher" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List()), "withParamsService" -> ObjectDefinition(List(Parameter("par1",
      ClazzRef(classOf[String]))), classOf[SimpleRecord], List())),
    Map("source" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List())),
    Map("sink" -> ObjectDefinition.noParam),
    Map("customTransformer" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List()), "withParamsTransformer" -> ObjectDefinition(List(Parameter("par1",
      ClazzRef(classOf[String]))), classOf[SimpleRecord], List())),
    ObjectDefinition.noParam,
    Map("processHelper" -> WithCategories(ClazzRef(ProcessHelper.getClass), List("cat1"))),
    EspTypeUtils.clazzAndItsChildrenDefinition(List(classOf[SampleEnricher], classOf[SimpleRecord], ProcessHelper.getClass))
  )

  it should "validated with success" in {
    val correctProcess = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("filter1", "#input.plainValueOpt + 1 > 1")
      .filter("filter2", "#input.plainValueOpt.abs + 1 > 1")
      .filter("filter3", "#input.intAsAny + 1 > 1")
      .processor("sampleProcessor1", "sampleEnricher")
      .enricher("sampleProcessor2", "out", "sampleEnricher")
      .buildVariable("bv1", "vars", "v1" -> "42",
        "mapVariable" -> "{ Field1: 'Field1Value', Field2: 'Field2Value', Field3: #input.plainValue }",
        "funkySpelVariable" -> "(#input.list.?[plainValue == 5]).![plainValue].contains(5)"
      )
      .sink("id2", "#processHelper.add(#processHelper, 2)", "sink")
    ProcessValidator.default(baseDefinition).validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = EspProcessBuilder.id("process1").exceptionHandler().source(duplicatedId, "source").sink(duplicatedId, "sink")
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find duplicated ids in switch" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("source", "source")
        .switch("switch", "''", "var",
          Case("'1'", GraphBuilder.sink(duplicatedId, "sink")),
          Case("'2'", GraphBuilder.sink(duplicatedId, "sink"))
        )
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find expression parse error" in {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .sink("id2", "wtf!!!", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError(_, _, _, _), _)) =>
    }
  }

  it should "find missing service error" in {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "customTransformer")
        .processor("id2", missingServiceId, "foo" -> "'bar'")
        .sink("sink", "#event.plainValue", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingService(_, _), _)) =>
    }

    val validDefinition = baseDefinition.withService(missingServiceId, Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(validDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find redundant service parameters" in {
    val serviceId = "serviceId"
    val definition = baseDefinition.withService(serviceId)

    val redundantServiceParameter = "foo"

    val processWithInvalidServiceInvocation =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .processorEnd("id2", serviceId, redundantServiceParameter -> "'bar'")

    ProcessValidator.default(definition).validate(processWithInvalidServiceInvocation) should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(_, _), _)) =>
    }
  }

  it should "find missing source" in {
    val serviceId = "serviceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .filter("filter", "#input != null")
        .processorEnd("id2", serviceId, "foo" -> "'bar'")

    val definition = ObjectProcessDefinition.empty.withService(serviceId, Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(definition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved references input", "filter", None, "#input != null"), List(MissingSourceFactory(_, _)))) =>
    }
  }


  it should "find missing custom node" in {
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "out", "notExisting", "dummy" -> "input")
        .filter("filter", "#out != null")
        .sink("id2", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingCustomNodeExecutor("notExisting", "custom"), Nil)) =>
    }
  }

  it should "find missing parameter for exception handler" in {
    val process = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").sink("id2", "sink")
    val definition = baseDefinition.withExceptionHandlerFactory(Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(definition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(_, _), _)) =>
    }
  }

  it should "find usage of unresolved plain variables" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildVariable("bv1", "doesExist", "v1" -> "42")
      .filter("sampleFilter", "#doesExist['v1'] + #doesNotExist1 + #doesNotExist2 > 10")
      .sink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved references doesNotExist1, doesNotExist2", "sampleFilter", None, _), _)) =>
    }
  }

  it should "find usage of non references" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10")
      .filter("sampleFilter2", "input.plainValue > 10")
      .sink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'input' occurred. Maybe you missed '#' in front of it?", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "find usage of fields that does not exist in object" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.value1.value2 > 10")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .sink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }


  it should "find not existing variables after custom node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .sink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "find not existing variables after split" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .split("split1",
        GraphBuilder.sink("id2", "sink"),
        GraphBuilder.filter("sampleFilter2", "#input.value1.value3 > 10").sink("id3", "sink")
      )

    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "validate custom node return type" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter1", "#out1.value2")
      .filter("sampleFilter2", "#out1.terefere")
      .sink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'",
      "sampleFilter2", None, "#out1.terefere"), _)) =>

    }
  }

  //TODO: zrobic cos zeby dalo sie jednak cos tu walidowac...
  it should "allow unknown vars in custom node params" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar")
      .sink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Valid(_) =>
    }

  }

  private val definitionWithTypedSource = baseDefinition.copy(sourceFactories
    = Map("source" -> ObjectDefinition.noParam.copy(returnType = ClazzRef(classOf[SimpleRecord]))))

  private val definitionWithTypedSourceAndTransformNode =
    definitionWithTypedSource.withCustomStreamTransformer("custom", classOf[AnotherSimpleRecord], Parameter("par1", ClazzRef(classOf[String])))

  it should "find usage of fields that does not exist in option object" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValueOpt.terefere > 10")
      .sink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type 'scala.math.BigDecimal'", "sampleFilter1", None, _), _)) =>
    }
  }

  it should "return field/property names in errors" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .split("split",
        GraphBuilder.processorEnd("p1", "withParamsService", "par1" -> "#terefere"),
        GraphBuilder.customNode("c1", "output", "withParamsTransformer", "par1" -> "{").sink("id2", "sink"),
        GraphBuilder.buildVariable("v1", "output", "par1" -> "#terefere22")
          .sink("id3", "sink")
      )

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParseError("Unresolved references terefere", "p1", Some("par1"), _),
      List(
      ExpressionParseError("EL1044E:(pos 0): Unexpectedly ran out of input", "c1", Some("par1"), _),
      ExpressionParseError("Unresolved references terefere22", "v1", Some("par1"), _))
      )) =>
    }
  }

  case class SimpleRecord(value1: AnotherSimpleRecord, plainValue: BigDecimal, plainValueOpt: Option[BigDecimal], intAsAny: Any, list: java.util.List[SimpleRecord]) {
    private val privateValue = "priv"

    def invoke1: Future[AnotherSimpleRecord] = ???

    def invoke2: State[ContextWithLazyValuesProvider, AnotherSimpleRecord] = ???

    def someMethod(a: Int): Int = ???
  }

  case class AnotherSimpleRecord(value2: Long)

  import scala.collection.JavaConversions._

  class SampleEnricher extends Service {
    def invoke()(implicit ec: ExecutionContext) = Future(SimpleRecord(AnotherSimpleRecord(1), 2, Option(2), 1, List.empty[SimpleRecord]))
  }

  object ProcessHelper {
    def add(a: Int, b: Int) = a + b
  }

}