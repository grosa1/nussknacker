package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

/**
  * Source with methods specific for Flink
  *
  * @tparam T - type of the event that is generated by flink source function.
  *             This is needed to handle e.g. syntax suggestions in UI (in sources with explicite @MethodToInvoke).
  */
//TODO: remove type T parameter
trait FlinkSource[T] extends Source[T] {

  def sourceStream(env: StreamExecutionEnvironment,
                   flinkNodeContext: FlinkCustomNodeContext): DataStream[Context]

}

/**
  * Support for test mechanism for typical flink sources.
  *
  * @tparam Raw - type of raw event that is generated by flink source function.
  *             This is needed to handle e.g. syntax suggestions in UI (in sources with explicite @MethodToInvoke).
  */
trait FlinkSourceTestSupport[Raw] extends FlinkIntermediateRawSource[Raw] with SourceTestSupport[Raw] { self: Source[Raw] =>

  //TODO: design better way of handling test data in generic FlinkSource
  //Probably we *still* want to use CollectionSource (and have some custom logic in parser if needed), but timestamps
  //have to be handled here for now
  def timestampAssignerForTest : Option[TimestampWatermarkHandler[Raw]]

}

/**
  * Typical source with methods specific for Flink, user has only to define Source function.
  *
  * @tparam Raw - type of raw event that is generated by flink source function.
  *             This is needed to handle e.g. syntax suggestions in UI (in sources with explicite @MethodToInvoke).
  */
trait BasicFlinkSource[Raw] extends FlinkSource[Raw] with FlinkIntermediateRawSource[Raw] {

  def flinkSourceFunction: SourceFunction[Raw]

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    prepareSourceStream(env, flinkNodeContext, flinkSourceFunction)
  }
}