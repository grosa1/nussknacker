package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import pl.touk.nussknacker.engine.util.KeyedValue

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object KeyValueHelperTypeInformation {
  // It is helper function for interop with java - e.g. in case when you want to have KeyedEvent[POJO, POJO]
  def typeInformation[K, V](keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[K, V]] = {
    implicit val implicitKeyTypeInformation: TypeInformation[K] = keyTypeInformation
    implicit val implicitValueTypeInformation: TypeInformation[V] = valueTypeInformation
    // TODO: Use better TypeInformation
    TypeInformation.of(new TypeHint[KeyedValue[K, V]] {})
  }

  // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
  def typeInformation[V](valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[String, V]] = {
    KeyValueHelperTypeInformation.typeInformation(TypeInformation.of(classOf[String]), valueTypeInformation)
  }
}
