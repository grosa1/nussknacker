package pl.touk.nussknacker.engine.lite.components

import cats.data.Validated
import cats.data.Validated.Valid
import io.circe.Json
import io.circe.Json.{Null, fromInt, fromLong, fromString, obj}
import org.apache.kafka.clients.producer.ProducerRecord
import org.everit.json.schema.{Schema => EveritSchema}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.components.utils.JsonTestData._
import pl.touk.nussknacker.engine.lite.util.test.KafkaConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.engine.util.test.{RunListResult, RunResult}
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

class LiteKafaUniversaJsonFunctionalTest extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside
  with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage with FunctionalTestMixin {

  import LiteKafkaComponentProvider._
  import SpecialSpELElement._
  import pl.touk.nussknacker.engine.spel.Implicits._
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  test("should test end to end kafka json data at sink and source / handling nulls and empty json" ) {
    val testData = Table(
      ("config", "result"),
      (oConfig(InputEmptyObject, schemaObjNull, schemaObjNull), valid(obj())),
      (oConfig(InputEmptyObject, schemaObjNull, schemaObjNull, OutputField), oValid(Null)),
      (oConfig(Null, schemaObjNull, schemaObjNull), oValid(Null)),
      (oConfig(Null, schemaObjNull, schemaObjNull, OutputField), oValid(Null)),

      (oConfig(InputEmptyObject, schemaObjString, schemaObjString), valid(obj())),
      (oConfig(InputEmptyObject, schemaObjString, schemaObjString, OutputField), valid(obj())), //FIXME: it should throw exception at runtime

      (oConfig(InputEmptyObject, schemaObjUnionNullString, schemaObjUnionNullString, OutputField), oValid(Null)),
      (oConfig(InputEmptyObject, schemaObjUnionNullString, schemaObjUnionNullString), valid(obj())),
      (oConfig(Null, schemaObjUnionNullString, schemaObjUnionNullString), oValid(Null)),
      (oConfig(Null, schemaObjUnionNullString, schemaObjUnionNullString, OutputField), oValid(Null)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  // TODO: add more tests for primitives, logical tests, unions and so on, add random tests - like in LiteKafkaAvroSchemaFunctionalTest
  test("should test end to end kafka json data at sink and source / handling primitives..") {
    val testData = Table(
      ("config", "result"),
      //Primitive integer validations
      // FIXME handle minimum > MIN_VALUE && maximum < MAX_VALUE) as an Integer to make better interoperability between json and avro?
      //      (sConfig(fromLong(Integer.MAX_VALUE.toLong + 1), longSchema, integerRangeSchema), invalidTypes("path 'Value' actual: 'Long' expected: 'Integer'")),
      (sConfig(sampleInteger, schemaIntegerRange, schemaInteger), valid(fromInt(1))),
      (sConfig(fromLong(Integer.MAX_VALUE), schemaIntegerRange, schemaIntegerRange), valid(fromInt(Integer.MAX_VALUE))),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  // TODO: add more tests for handling objects..
  test("should test end to end kafka json data at sink and source / handling objects..") {
    val testData = Table(
      ("config", "result"),
      (oConfig(sampleObjFirstLastName, schemaObjObjFirstLastNameRequired, schemaObjObjFirstLastNameRequired), oValid(sampleObjFirstLastName)),
      (oConfig(sampleInteger, schemaObjInteger, schemaObjObjFirstLastNameRequired, sampleSpELFirstLastName), oValid(sampleObjFirstLastName)),

      //Additional fields turn on
      (oConfig(sampleMapAny, schemaObjMapAny, schemaObjMapAny), oValid(sampleMapAny)),
      (oConfig(sampleInteger, schemaObjInteger, schemaObjMapAny, sampleMapSpELAny), oValid(sampleMapAny)),
      (oConfig(sampleObjFirstLastName, schemaObjObjFirstLastNameRequired, schemaObjMapAny), oValid(sampleObjFirstLastName)),
      (oConfig(sampleInteger, schemaObjInteger, schemaObjMapAny, sampleSpELFirstLastName), oValid(sampleObjFirstLastName)),

      (oConfig(sampleMapInteger, schemaObjMapInteger, schemaObjMapInteger), oValid(sampleMapInteger)),
      (oConfig(sampleInteger, schemaObjInteger, schemaObjMapInteger, sampleMapSpELInteger), oValid(sampleMapInteger)),
      (sConfig(sampleInteger, schemaInteger, schemaObjString, Map("redundant" -> "red")), invalid(Nil, List("field"), List("redundant"))),

      (sConfig(sampleMapString, schemaMapString, schemaMapInteger), invalid(List("path 'value' actual: 'String' expected: 'Long'"), Nil, Nil)),
      (sConfig(sampleMapString, schemaObjString, schemaMapInteger), invalid(List("path 'field' actual: 'String' expected: 'Long'"), Nil, Nil)),
      (oConfig(sampleMapAny, schemaObjMapAny, schemaObjMapInteger), invalid(List("path 'field.value' actual: 'Unknown' expected: 'Long'"), Nil, Nil)),

      (sConfig(sampleObPerson, nameAndLastNameSchema, nameAndLastNameSchema), valid(sampleObPerson)),
      (sConfig(sampleObPerson, personSchema, nameAndLastNameSchema), valid(sampleObPerson)),
      (sConfig(sampleObPerson, personSchema, nameAndLastNameSchema(schemaInteger)), valid(sampleObPerson)),
      (sConfig(sampleObPerson, personSchema, nameAndLastNameSchema(schemaString)), invalid(List("path 'age' actual: 'Long' expected: 'String'"), Nil, Nil)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  test("sink with schema with additionalProperties: true/{schema}") {
    //This tests runs scenario which passes #input directly to Sink using raw editor. Scenario is triggered with `{}` message.
    val lax = List(ValidationMode.lax)
    val strict = List(ValidationMode.strict)
    val strictAndLax = ValidationMode.values
    def invalidType(msg: String) = invalid(List(msg), Nil, Nil)

    //@formatter:off
    val testData = Table(
      ("sourceSchema",        "sinkSchema",                         "validationModes",  "result"),
      (schemaMapAny,          schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaMapString,       schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaMapObjPerson,    schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaListIntegers,    schemaMapAny,                         strictAndLax,       invalidType("actual: 'List[Long]' expected: 'Map[String, Any]'")),
      (personSchema,          schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaMapAny,          schemaMapString,                      strict,             invalidType("path 'value' actual: 'Unknown' expected: 'String'")),
      (schemaMapAny,          schemaMapString,                      lax,                valid(obj())),
      (schemaMapString,       schemaMapString,                      strictAndLax,       valid(obj())),
      (schemaMapStringOrInt,  schemaMapString,                      strict,             invalidType("path 'value' actual: 'String | Long' expected: 'String'")),
      (schemaMapStringOrInt,  schemaMapString,                      lax,                valid(obj())),
      (schemaMapObjPerson,    schemaMapString,                      strictAndLax,       invalidType("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String'")),
      (schemaListIntegers,    schemaMapString,                      strictAndLax,       invalidType("actual: 'List[Long]' expected: 'Map[String, String]'")),
      (personSchema,          schemaMapString,                      strictAndLax,       invalidType("path 'age' actual: 'Long' expected: 'String'")),
      (schemaMapAny,          schemaMapStringOrInt,                 strict,             invalidType("path 'value' actual: 'Unknown' expected: 'String | Long'")),
      (schemaMapAny,          schemaMapStringOrInt,                 lax,                valid(obj())),
      (schemaMapString,       schemaMapStringOrInt,                 strictAndLax,       valid(obj())),
      (schemaMapStringOrInt,  schemaMapStringOrInt,                 strictAndLax,       valid(obj())),
      (schemaMapObjPerson,    schemaMapStringOrInt,                 strictAndLax,       invalidType("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String | Long'")),
      (schemaListIntegers,    schemaMapStringOrInt,                 strictAndLax,       invalidType("actual: 'List[Long]' expected: 'Map[String, String | Long]'")),
      (personSchema,          schemaMapStringOrInt,                 strictAndLax,       valid(obj())),
      (personSchema,          nameAndLastNameSchema,                strictAndLax,       valid(obj())),
      (personSchema,          nameAndLastNameSchema(schemaInteger), strictAndLax,       valid(obj())),
      (personSchema,          nameAndLastNameSchema(schemaString),  strictAndLax,       invalidType("path 'age' actual: 'Long' expected: 'String'")),
    )
    //@formatter:on

    forAll(testData) {
      (sourceSchema: EveritSchema, sinkSchema: EveritSchema, validationModes: List[ValidationMode], expected: Validated[_, RunResult[_]]) =>
        validationModes.foreach { mode =>
          val results = runWithValueResults(oConfig(InputEmptyObject, sourceSchema, sinkSchema, output = Input, Some(mode)))
          results shouldBe expected
        }
    }
  }

  test("should catch runtime errors") {
    val testData = Table(
      ("config", "result"),
      //Errors at sources
      (oConfig(fromString("invalid"), schemaObjObjFirstLastNameRequired, schemaObjObjFirstLastNameRequired, Input), "#/field: expected type: JSONObject, found: String"),
      (oConfig(Json.Null, schemaObjObjFirstLastNameRequired, schemaObjObjFirstLastNameRequired, Input), "#/field: expected type: JSONObject, found: Null"),
      (oConfig(obj("first" -> fromString("")), schemaObjObjFirstLastNameRequired, schemaObjObjFirstLastNameRequired, Input), "#/field: required key [last] not found"),
      (oConfig(obj("t1" -> fromString("1")), schemaObjMapInteger, schemaObjMapAny), "#/field/t1: expected type: Integer, found: String"),
      (sConfig(obj("t1" -> fromString("1"), "field" -> fromString("1")), schemaObjString, schemaMapAny), "#: extraneous key [t1] is not permitted"),
    )

    forAll(testData) { (config: ScenarioConfig, expected: String) =>
      val results = runWithValueResults(config)
      val message = results.validValue.errors.head.throwable.asInstanceOf[RuntimeException].getMessage

      message shouldBe expected
    }
  }

  private def runWithValueResults(config: ScenarioConfig) =
    runWithResults(config).map(_.mapSuccesses(r => CirceUtil.decodeJsonUnsafe[Json](r.value(), "invalid json string")))

  private def runWithResults(config: ScenarioConfig): RunnerListResult[ProducerRecord[String, String]] = {
    val jsonScenario: CanonicalProcess = createScenario(config)
    runner.registerJsonSchema(config.sourceTopic, config.sourceSchema)
    runner.registerJsonSchema(config.sinkTopic, config.sinkSchema)

    val input = KafkaConsumerRecord[String, String](config.sourceTopic, config.inputData.toString())
    val result = runner.runWithStringData(jsonScenario, List(input))
    result
  }

  private def createScenario(config: ScenarioConfig) =
    ScenarioBuilder
      .streamingLite("check json validation")
      .source(sourceName, KafkaUniversalName,
        TopicParamName -> s"'${config.sourceTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(sinkName, KafkaUniversalName,
        TopicParamName -> s"'${config.sinkTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "",
        SinkValueParamName -> s"${config.sinkDefinition}",
        SinkRawEditorParamName -> "true",
        SinkValidationModeParameterName -> s"'${config.validationModeName}'"
      )

  case class ScenarioConfig(topic: String, inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic = s"$topic-input"
    lazy val sinkTopic = s"$topic-output"
  }

  //ObjectValid -> config with object as a input
  private def oConfig(inputData: Any, sourceSchema: EveritSchema, sinkSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    val sinkDefinition = output match {
      case element: SpecialSpELElement if List(EmptyMap, Input).contains(element) => element
      case any => Map(ObjectFieldName -> any)
    }

    val input = inputData match {
      case InputEmptyObject => obj()
      case in: Json => obj(ObjectFieldName -> in)
      case in => throw new IllegalArgumentException(s"Not allowed type of data: $in.")
    }

    ScenarioConfig(randomTopic, input, sourceSchema, sinkSchema, sinkDefinition.toSpELLiteral, validationMode)
  }

  //ObjectValid -> valid success object with base field
  private def oValid(data: Json): Valid[RunListResult[Json]] =
    valid(obj(ObjectFieldName -> data))

  private def sConfig(inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, output.toSpELLiteral, validationMode)


}