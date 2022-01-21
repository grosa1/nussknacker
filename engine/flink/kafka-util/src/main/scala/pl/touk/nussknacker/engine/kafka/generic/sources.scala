package pl.touk.nussknacker.engine.kafka.generic

import io.circe.Decoder
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory._
import pl.touk.nussknacker.engine.kafka.serialization.schemas._
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory

import java.util

//TODO: Move it to source package
object sources {

  class GenericJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, java.util.Map[_, _]](
    new FixedValueDeserializationSchemaFactory(JsonMapDeserialization), jsonFormatterFactory, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))

  class GenericTypedJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, TypedMap](
    new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization), jsonFormatterFactory, processObjectDependencies, new FlinkKafkaSourceImplFactory(None)) with BaseGenericTypedJsonSourceFactory

  class DelayedGenericTypedJsonSourceFactory(formatterFactory: RecordFormatterFactory,
                                             processObjectDependencies: ProcessObjectDependencies,
                                             timestampAssigner: Option[TimestampWatermarkHandler[TypedJson]])
    extends DelayedKafkaSourceFactory[String, TypedMap](
      new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization),
      formatterFactory,
      processObjectDependencies,
      new FlinkKafkaDelayedSourceImplFactory(timestampAssigner, TypedJsonTimestampFieldAssigner(_)))

  implicit val ti1: TypeInformation[util.Map[_, _]] = TypeInformation.of(classOf[java.util.Map[_, _]])
  implicit val ti2: TypeInformation[TypedMap] = TypeInformation.of(classOf[TypedMap])

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToMap)

  object JsonTypedMapDeserialization extends EspDeserializationSchema[TypedMap](deserializeToTypedMap)

  //TOOD: better error handling?
  class JsonDecoderDeserialization[T:Decoder:TypeInformation] extends EspDeserializationSchema[T](CirceUtil.decodeJsonUnsafe[T](_))

}
