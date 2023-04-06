package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.Registration
import com.twitter.chill._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.runtime.kryo.FlinkChillPackageRegistrar

import scala.collection.immutable.{BitSet, HashMap, HashSet, ListMap, ListSet, NumericRange, Queue, Range, SortedMap, SortedSet}
import scala.collection.mutable
import scala.collection.mutable.{ArraySeq, Buffer, ListBuffer, BitSet => MBitSet, HashMap => MHashMap, HashSet => MHashSet, Map => MMap, Queue => MQueue, Set => MSet}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.matching.Regex


object ChillScalaSerializersRegistrar {

  private def getTypesWithSerializers(): mutable.Seq[(Class[_], KSerializer[_])] = {
    var list = ListBuffer[(Class[_], KSerializer[_])]()
    class StealingRegistrationsKryo extends Kryo {
      override def register(`type`: Class[_], serializer: KSerializer[_]): Registration = {
        val tuple: (Class[_], KSerializer[_]) = (`type`, serializer)
        list += tuple
        super.register(`type`, serializer)
      }
    }

    val k = new StealingRegistrationsKryo()
    val r = new AllScalaRegistrar
    r(k)
    list
  }

  def registerIn(config: ExecutionConfig): Unit = {
    getTypesWithSerializers().foreach {
      case (c, s) =>
        config.getDefaultKryoSerializerClasses.put(c, s.getClass)
    }
  }
}



class ScalaCollectionsRegistrar extends IKryoRegistrar {
  def apply(newK: Kryo): Unit = {
    // for binary compat this is here, but could be moved to RichKryo
    def useField[T](cls: Class[T]): Unit = {
      val fs = new com.esotericsoftware.kryo.serializers.FieldSerializer(newK, cls)
      fs.setIgnoreSyntheticFields(false) // scala generates a lot of these attributes
      newK.register(cls, fs)
    }
    // The wrappers are private classes:
    useField(List(1, 2, 3).asJava.getClass)
    useField(List(1, 2, 3).iterator.asJava.getClass)
    useField(Map(1 -> 2, 4 -> 3).asJava.getClass)
    useField(new _root_.java.util.ArrayList().asScala.getClass)
    useField(new _root_.java.util.HashMap().asScala.getClass)

    /*
     * Note that subclass-based use: addDefaultSerializers, else: register
     * You should go from MOST specific, to least to specific when using
     * default serializers. The FIRST one found is the one used
     */
    newK
      .forTraversableSubclass(ArraySeq.empty[Any], isImmutable = false)
      .forSubclass[BitSet](new BitSetSerializer)
      .forSubclass[SortedSet[Any]](new SortedSetSerializer)
      .forClass[Some[Any]](new SomeSerializer[Any])
      .forClass[Left[Any, Any]](new LeftSerializer[Any, Any])
      .forClass[Right[Any, Any]](new RightSerializer[Any, Any])
      .forTraversableSubclass(Queue.empty[Any])
      // List is a sealed class, so there are only two subclasses:
      .forTraversableSubclass(List.empty[Any])
      // Add ListBuffer subclass before Buffer to prevent the more general case taking precedence
      .forTraversableSubclass(ListBuffer.empty[Any], isImmutable = false)
      // add mutable Buffer before Vector, otherwise Vector is used
      .forTraversableSubclass(Buffer.empty[Any], isImmutable = false)
      // Vector is a final class
      .forTraversableClass(Vector.empty[Any])
      .forTraversableSubclass(ListSet.empty[Any])
      // specifically register small sets since Scala represents them differently
      .forConcreteTraversableClass(Set[Any](Symbol("a")))
      .forConcreteTraversableClass(Set[Any](Symbol("a"), Symbol("b")))
      .forConcreteTraversableClass(Set[Any](Symbol("a"), Symbol("b"), Symbol("c")))
      .forConcreteTraversableClass(Set[Any](Symbol("a"), Symbol("b"), Symbol("c"), Symbol("d")))
      // default set implementation
      .forConcreteTraversableClass(HashSet[Any](Symbol("a"), Symbol("b"), Symbol("c"), Symbol("d"), Symbol("e")))
      // specifically register small maps since Scala represents them differently
      .forConcreteTraversableClass(Map[Any, Any](Symbol("a") -> Symbol("a")))
      .forConcreteTraversableClass(Map[Any, Any](Symbol("a") -> Symbol("a"), Symbol("b") -> Symbol("b")))
      .forConcreteTraversableClass(Map[Any, Any](Symbol("a") -> Symbol("a"), Symbol("b") -> Symbol("b"), Symbol("c") -> Symbol("c")))
      .forConcreteTraversableClass(Map[Any, Any](Symbol("a") -> Symbol("a"), Symbol("b") -> Symbol("b"), Symbol("c") -> Symbol("c"), Symbol("d") -> Symbol("d")))
      // default map implementation
      .forConcreteTraversableClass(
        HashMap[Any, Any](Symbol("a") -> Symbol("a"), Symbol("b") -> Symbol("b"), Symbol("c") -> Symbol("c"), Symbol("d") -> Symbol("d"), Symbol("e") -> Symbol("e")))
      // The normal fields serializer works for ranges
      .registerClasses(Seq(
        classOf[Range.Inclusive],
        classOf[NumericRange.Inclusive[_]],
        classOf[NumericRange.Exclusive[_]]))
      // Add some maps
      .forSubclass[SortedMap[Any, Any]](new SortedMapSerializer)
      .forTraversableSubclass(ListMap.empty[Any, Any])
      .forTraversableSubclass(HashMap.empty[Any, Any])
      // The above ListMap/HashMap must appear before this:
      .forTraversableSubclass(Map.empty[Any, Any])
      // here are the mutable ones:
      .forTraversableClass(MBitSet.empty, isImmutable = false)
      .forTraversableClass(MHashMap.empty[Any, Any], isImmutable = false)
      .forTraversableClass(MHashSet.empty[Any], isImmutable = false)
      .forTraversableSubclass(MQueue.empty[Any], isImmutable = false)
      .forTraversableSubclass(MMap.empty[Any, Any], isImmutable = false)
      .forTraversableSubclass(MSet.empty[Any], isImmutable = false)
  }
}

// In Scala 2.13 all java collections class wrappers were rewritten from case class to regular class. Now kryo does not
// serialize them properly, so this class was added to fix this issue. It might not be needed in the future, when flink
// or twitter-chill updates kryo.
class JavaWrapperScala2_13Registrar extends IKryoRegistrar {
  def apply(newK: Kryo): Unit = {
    newK.register(JavaWrapperScala2_13Serializers.mapSerializer.wrapperClass, JavaWrapperScala2_13Serializers.mapSerializer)
    newK.register(JavaWrapperScala2_13Serializers.setSerializer.wrapperClass, JavaWrapperScala2_13Serializers.setSerializer)
    newK.register(JavaWrapperScala2_13Serializers.listSerializer.wrapperClass, JavaWrapperScala2_13Serializers.listSerializer)
  }
}

/** Registers all the scala (and java) serializers we have */
class AllScalaRegistrar extends IKryoRegistrar {
  def apply(k: Kryo): Unit = {
    val col = new ScalaCollectionsRegistrar
    col(k)

    val jcol = new JavaWrapperCollectionRegistrar
    jcol(k)

    val jmap = new JavaWrapperScala2_13Registrar
    jmap(k)

    // Register all 22 tuple serializers and specialized serializers
    ScalaTupleSerialization.register(k)
    k.forClass[Symbol](new KSerializer[Symbol] {
      override def isImmutable = true
      def write(k: Kryo, out: Output, obj: Symbol): Unit = { out.writeString(obj.name) }
      def read(k: Kryo, in: Input, cls: Class[Symbol]) = Symbol(in.readString)
    }).forSubclass[Regex](new RegexSerializer)
      .forClass[ClassTag[Any]](new ClassTagSerializer[Any])
      .forSubclass[Manifest[Any]](new ManifestSerializer[Any])
      .forSubclass[scala.Enumeration#Value](new EnumerationSerializer)

    // use the singleton serializer for boxed Unit
    val boxedUnit = scala.runtime.BoxedUnit.UNIT
    k.register(boxedUnit.getClass, new SingletonSerializer(boxedUnit))
    new FlinkChillPackageRegistrar().registerSerializers(k)
  }
}

private class JavaWrapperScala2_13Serializer[T](val wrapperClass: Class[_ <: T], val transform: Any => T) extends KSerializer[T] {
  private val underlyingMethodOpt = {
    try Some(wrapperClass.getDeclaredMethod("underlying"))
    catch {
      case _: Exception =>
        None
    }
  }

  override def write(kryo: Kryo, out: Output, obj: T): Unit =
  // If the object is the wrapper, simply serialize the underlying Scala object.
  // Otherwise, serialize the object itself.
    if (obj.getClass == wrapperClass && underlyingMethodOpt.isDefined) {
      kryo.writeClassAndObject(out, underlyingMethodOpt.get.invoke(obj))
    } else {
      kryo.writeClassAndObject(out, obj)
    }

  override def read(kryo: Kryo, in: Input, clz: Class[T]): T =
    transform(kryo.readClassAndObject(in))
}


import _root_.java.util.{List => JList, Map => JMap, Set => JSet}


private object JavaWrapperScala2_13Serializers {
  // The class returned by asJava (scala.collection.convert.Wrappers$MapWrapper).
  private val mapWrapperClass: Class[_ <: JMap[Int, Int]] = mutable.Map.empty[Int, Int].asJava.getClass
  val mapSerializer = new JavaWrapperScala2_13Serializer[JMap[_, _]](mapWrapperClass, {
    case scalaMap: mutable.Map[_, _] =>
      scalaMap.asJava
    case javaMap: JMap[_, _] =>
      javaMap
  })

  private val listWrapperClass: Class[_ <: JList[Int]] = mutable.Buffer.empty[Int].asJava.getClass
  val listSerializer = new JavaWrapperScala2_13Serializer[JList[_]](listWrapperClass, {
    case scalaList: mutable.Buffer[_] =>
      scalaList.asJava
    case javaList: JList[_] =>
      javaList
  })

  private val setWrapperClass: Class[_ <: JSet[Int]] = mutable.Set.empty[Int].asJava.getClass
  val setSerializer = new JavaWrapperScala2_13Serializer[JSet[_]](setWrapperClass, {
    case scalaSet: mutable.Set[_] =>
      scalaSet.asJava
    case javaSet: JSet[_] =>
      javaSet
  })
}