package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

case object CustomFilter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(@ParamName("expression") expression: LazyParameter[java.lang.Boolean]): FlinkCustomStreamTransformation
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
    start
      .filter(ctx.lazyParameterHelper.lazyFilterFunction(expression))
      .map(ValueWithContext[AnyRef](null, _)))

}
