package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.ValueWithContext

object TypeInfoHelper {

  def valueWithContextTypeInfo: TypeInformation[ValueWithContext[Any]] = {
    //FIXME: TypeInformation
    TypeInformation.of(classOf[ValueWithContext[Any]])
  }

}
