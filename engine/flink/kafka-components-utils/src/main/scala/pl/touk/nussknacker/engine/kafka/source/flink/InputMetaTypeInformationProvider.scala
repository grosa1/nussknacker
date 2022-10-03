package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.MapTypeInfo
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, ScalaCaseClassSerializer}
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.kafka.source.InputMeta

object InputMetaTypeInformationProvider {

  // It should be synchronized with InputMeta.withType
  def typeInformation[K](keyTypeInformation: TypeInformation[K]): CaseClassTypeInfo[InputMeta[K]] = {
    val fieldNames = List(
      InputMeta.keyParameterName,
      "topic",
      "partition",
      "offset",
      "timestamp",
      "timestampType",
      "headers",
      "leaderEpoch"
    )
    val fieldTypes = List(
      keyTypeInformation,
      TypeInformation.of(classOf[String]),
      TypeInformation.of(classOf[Integer]),
      TypeInformation.of(classOf[java.lang.Long]),
      TypeInformation.of(classOf[java.lang.Long]),
      TypeInformation.of(classOf[TimestampType]),
      new MapTypeInfo(classOf[String], classOf[String]),
      TypeInformation.of(classOf[Integer])
    )
    val cls = classOf[InputMeta[K]]
    val fields = fieldNames.zip(fieldTypes)
    new ConcreteCaseClassTypeInfo[InputMeta[K]](cls, fields){
      override def createSerializer(config: ExecutionConfig) =
        new ScalaCaseClassSerializer[InputMeta[K]](cls, fields.map(_._2.createSerializer(config)).toArray)
    }
  }
}

/**
  * Implementation of customisation for TypeInformationDetection that provides type information for InputMeta.
  */
class InputMetaTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {
  override def customise(originalDetection: TypeInformationDetection): PartialFunction[TypingResult, TypeInformation[_]] = {
    case a:TypedObjectTypingResult if a.objType.klass == classOf[InputMeta[_]] =>
      InputMetaTypeInformationProvider.typeInformation(originalDetection.forType(a.fields(InputMeta.keyParameterName)))
  }

}