package pl.touk.nussknacker.engine.json.encode

import cats.data.Validated.condNel
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.{EmptySchema, ObjectSchema, ReferenceSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._
import pl.touk.nussknacker.engine.util.output._
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.SchemaOutputValidator

import scala.language.implicitConversions

private[encode] case class JsonSchemaExpected(schema: Schema, rootSchema: Schema) extends OutputValidatorExpected {
  override def expected: String = new JsonSchemaOutputValidatorPrinter(rootSchema).print(schema)
}

object JsonSchemaOutputValidator {

  private implicit class RichTypedClass(t: TypedClass) {
    val representsMapWithStringKeys: Boolean = {
      t.klass == classOf[java.util.Map[_, _]] && t.params.size == 2 && t.params.head == Typed.typedClass[String]
    }
  }
}

// root schema is a container for eventual ref schemas - in particular it can be the same schema as outputSchema
class JsonSchemaOutputValidator(validationMode: ValidationMode, outputSchema: Schema, rootSchema: Schema) extends SchemaOutputValidator with LazyLogging {

  import JsonSchemaOutputValidator._

  import scala.jdk.CollectionConverters._

  private val valid = Validated.Valid(())

  /**
    * To see what's we currently supporting see SwaggerBasedJsonSchemaTypeDefinitionExtractor as well
    */
  def validateTypingResultAgainstSchema(typingResult: TypingResult): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, outputSchema, None)

  //todo: add support for: enums, logical types
  final private def validateTypingResult(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema) match {
      case (_, referenceSchema: ReferenceSchema) => validateTypingResult(typingResult, referenceSchema.getReferredSchema, path)
      case (_, _: EmptySchema) => valid
      case (Unknown, _) => validateUnknownInputType(schema, path)
      case (union: TypedUnion, _) => validateUnionInputType(union, schema, path)
      case (tc: TypedClass, s: ObjectSchema) if tc.representsMapWithStringKeys => validateMapInputType(tc, tc.params.tail.head, s, path)
      case (typingResult: TypedObjectTypingResult, s: ObjectSchema) => validateRecordInputType(typingResult, s, path)
      case (_, _) => canBeSubclassOf(typingResult, schema, path)
    }
  }

  private def validateUnknownInputType(schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    validationMode match {
      case ValidationMode.lax => valid
      case ValidationMode.strict => invalid(Unknown, schema, path)
      case validationMode => throw new IllegalStateException(s"Unsupported validation mode $validationMode")
    }
  }

  private def validateUnionInputType(union: TypedUnion, schema: Schema, path: Option[String]) = {
    if (validationMode == ValidationMode.strict && !union.possibleTypes.forall(validateTypingResult(_, schema, path).isValid))
      invalid(union, schema, path)
    else if (validationMode == ValidationMode.lax && !union.possibleTypes.exists(validateTypingResult(_, schema, path).isValid))
      invalid(union, schema, path)
    else
      valid
  }

  private def validateMapInputType(mapTypedClass: TypedClass, mapValuesTypingResult: TypingResult, schema: ObjectSchema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    if (validationMode == ValidationMode.strict) {
      validateMapInputTypeStrict(mapTypedClass, mapValuesTypingResult, schema, path)
    } else {
      validateMapInputTypeLax(mapValuesTypingResult, schema, path)
    }
  }

  private def validateMapInputTypeStrict(mapTypedClass: TypedClass, mapValuesTypingResult: TypingResult, schema: ObjectSchema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    if (hasDefinedExplicitProps(schema) || schema.hasPatternProperties) {
      invalid(mapTypedClass, schema, path)
    } else if (schema.acceptsEverythingAsAdditionalProperty) {
      valid
    } else {
      validateTypingResult(mapValuesTypingResult, schema.getSchemaOfAdditionalProperties, buildFieldPath("value", path))
    }
  }

  private def hasDefinedExplicitProps(schema: ObjectSchema) = {
    !schema.getPropertySchemas.isEmpty
  }

  private def validateMapInputTypeLax(mapValuesTypingResult: TypingResult, schema: ObjectSchema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Unit] = {
    if (isPossibleToProvideValidInputUsingMapValueType(schema, mapValuesTypingResult)) {
      valid
    } else {
      invalid(mapValuesTypingResult, schema.getSchemaOfAdditionalProperties, buildFieldPath("value", path))
    }
  }

  private def isPossibleToProvideValidInputUsingMapValueType(objectSchema: ObjectSchema, mapValueType: TypingResult) = {
    val requiredPropertiesSchemas = objectSchema.requiredPropertiesSchemas
    if (requiredPropertiesSchemas.nonEmpty) {
      allSchemasMatchesType(requiredPropertiesSchemas.values.toList, mapValueType)
    } else if (objectSchema.acceptsEverythingAsAdditionalProperty) {
      true
    } else {
      val explicitPropsSchemas = objectSchema.getPropertySchemas.asScala.values.toList
      val patternPropsSchemas = objectSchema.patternProperties.values.toList
      val additionalPropertiesSchema = if (objectSchema.permitsAdditionalProperties()) List(objectSchema.getSchemaOfAdditionalProperties) else List()
      val schemasToCheck = additionalPropertiesSchema ++ patternPropsSchemas ++ explicitPropsSchemas
      atLeastOneSchemaMatchesType(schemasToCheck, mapValueType)
    }
  }

  private def allSchemasMatchesType(schemasToCheck: List[Schema], typingResult: TypingResult): Boolean = {
    !schemasToCheck.exists(schema => validateTypingResult(typingResult, schema, None).isInvalid)
  }

  private def atLeastOneSchemaMatchesType(schemasToCheck: List[Schema], typingResult: TypingResult): Boolean = {
    schemasToCheck.exists(schema => validateTypingResult(typingResult, schema, None).isValid)
  }

  private def validateRecordInputType(typingResult: TypedObjectTypingResult, schema: ObjectSchema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    val explicitProps = schema.getPropertySchemas.asScala.toMap
    val requiredProps = schema.getRequiredProperties.asScala.toSet
    val schemaFieldsValidation = validateRecordExplicitProperties(typingResult, explicitProps, path)

    val requiredPropsV = validateRecordRequiredProps(typingResult, explicitProps, requiredProps, path)
    val redundantPropsV = validateRecordRedundantProps(typingResult, schema, explicitProps, path)
    val (patternPropsV, inputFieldsMatchedByPatternProps) = validateRecordPatternProps(typingResult, schema, path)
    val foundAdditionalProps = findRecordAdditionalProps(typingResult, explicitProps.keySet, inputFieldsMatchedByPatternProps)
    val additionalPropsV = validateRecordAdditionalProps(schema, path, foundAdditionalProps)

    requiredPropsV.combine(schemaFieldsValidation)
      .combine(redundantPropsV)
      .combine(patternPropsV)
      .combine(additionalPropsV)
  }

  private def validateRecordRequiredProps(typingResult: TypedObjectTypingResult, explicitPropsSchemas: Map[String, Schema], requiredProps: Set[String], path: Option[String]): ValidatedNel[OutputValidatorMissingFieldsError, Unit] = {
    val requiredPropsNames = if (validationMode == ValidationMode.strict) {
      explicitPropsSchemas.keys.toSet
    } else {
      requiredProps
    }
    val missingProps = requiredPropsNames.filterNot(typingResult.fields.contains)
    condNel(missingProps.isEmpty, (), OutputValidatorMissingFieldsError(buildFieldsPaths(missingProps, path)))
  }

  private def validateRecordExplicitProperties(typingResult: TypedObjectTypingResult, schemaFields: Map[String, Schema], path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    validateFieldsType(typingResult.fields.filterKeysNow(schemaFields.contains), schemaFields, path)
  }

  private def validateRecordRedundantProps(typingResult: TypedObjectTypingResult, schema: ObjectSchema, schemaFields: Map[String, Schema], path: Option[String]): ValidatedNel[OutputValidatorRedundantFieldsError, Unit] = {
    val redundantFields = typingResult.fields.keySet.diff(schemaFields.keySet)
    condNel(redundantFields.isEmpty || schema.permitsAdditionalProperties(), (), OutputValidatorRedundantFieldsError(buildFieldsPaths(redundantFields, path)))
  }

  private def validateRecordPatternProps(typingResult: TypedObjectTypingResult, schema: ObjectSchema, path: Option[String]): (Validated[NonEmptyList[OutputValidatorError], Unit], Set[String]) = {
    val fieldsWithMatchedPatternsProperties = typingResult.fields.toList
      .map { case (fieldName, _) => fieldName -> schema.patternProperties.filterKeysNow(p => p.asPredicate().test(fieldName)).values.toList }
      .filter { case (_, schemas) => schemas.nonEmpty }

    val validation = fieldsWithMatchedPatternsProperties.flatMap { case (fieldName, schemas) =>
      schemas.map(schema => validateTypingResult(typingResult.fields(fieldName), schema, path))
    }
      .sequence
      .map(_ => (): Unit)
    (validation, fieldsWithMatchedPatternsProperties.map { case (name, _) => name }.toSet)
  }

  private def findRecordAdditionalProps(typingResult: TypedObjectTypingResult, schemaFields: Set[String], propertiesMatchedByPatternProperties: Set[String]): Map[String, TypingResult] = {
    typingResult.fields.filterKeysNow(k => !schemaFields.contains(k) && !propertiesMatchedByPatternProperties.contains(k))
  }

  private def validateRecordAdditionalProps(schema: ObjectSchema, path: Option[String], additionalFieldsToValidate: Map[String, TypingResult]): ValidatedNel[OutputValidatorError, Unit] = {
    if (additionalFieldsToValidate.isEmpty || schema.getSchemaOfAdditionalProperties == null) {
      valid
    } else {
      validateFieldsType(additionalFieldsToValidate, additionalFieldsToValidate.mapValuesNow(_ => schema.getSchemaOfAdditionalProperties), path)
    }
  }

  private def validateFieldsType(fieldsToValidate: Map[String, TypingResult], schemas: Map[String, Schema], path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    fieldsToValidate.flatMap { case (key, value) =>
      val fieldPath = buildFieldPath(key, path)
      schemas.get(key).map(fieldSchema => validateTypingResult(value, fieldSchema, fieldPath))
    }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
  }

  /**
   * TODO: Consider verification class instead of using .canBeSubclassOf from Typing - we want to avoid:
   * * Unknown.canBeSubclassOf(Any) => true
   * * Long.canBeSubclassOf(Integer) => true
   * Should we use strict verification at json?
   */
  private def canBeSubclassOf(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    val schemaAsTypedResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema, Some(rootSchema)).typingResult
    condNel(typingResult.canBeSubclassOf(schemaAsTypedResult), (),
      OutputValidatorTypeError(path, typingResult, JsonSchemaExpected(schema, rootSchema))
    )
  }

  private def invalid(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(typeError(typingResult, schema, path))

  private def typeError(typingResult: TypingResult, schema: Schema, path: Option[String]) =
    OutputValidatorTypeError(path, typingResult, JsonSchemaExpected(schema, rootSchema))

  private def buildFieldsPaths(fields: Set[String], path: Option[String]) = fields.flatMap(buildFieldPath(_, path))

  private def buildFieldPath(key: String, path: Option[String], useIndexer: Boolean = false) = Some(
    path.map(p => if (useIndexer) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )
}
