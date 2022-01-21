package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}

class ReturningClassInstanceSource extends SourceFactory  {

  @MethodToInvoke
  def source(@ParamName("Additional class")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )  additionalClass: String) =
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, List.empty, None, Typed.typedClass(Class.forName(additionalClass)))(TypeInformation.of(classOf[Any]))

}
case class ReturningTestCaseClass(someMethod: String)