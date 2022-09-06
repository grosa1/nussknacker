package pl.touk.nussknacker.engine.management.sample.global

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError.ArgumentTypeError
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, GenericType, MethodTypeInfo, Parameter, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import scala.jdk.CollectionConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}


object LambdaFunction {
  case class Lambda[A, R](f: A => R)

  @GenericType(typingFunction = classOf[isNotHelper])
  def isNot(t: Int): Lambda[Int, Boolean] = Lambda[Int, Boolean](i => i != t)

  private class isNotHelper extends TypingFunction {
    override def signatures: Option[NonEmptyList[MethodTypeInfo]] = Some(NonEmptyList.of(
      MethodTypeInfo(List(Parameter("i", Typed[Int])), None, Typed.fromDetailedType[Lambda[Int, Boolean]])
    ))

    override def computeResultType(arguments: List[typing.TypingResult]): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
      Typed.fromDetailedType[Lambda[Int, Boolean]].validNel
  }


  @GenericType(typingFunction = classOf[filterHelper])
  def filter[T](lst: java.util.List[T], f: Lambda[T, Boolean]): java.util.List[T] =
    lst.asScala.filter(f.f).toList.asJava

  private class filterHelper extends TypingFunction {
    override def signatures: Option[NonEmptyList[MethodTypeInfo]] = Some(NonEmptyList.of(
      MethodTypeInfo(
        List(Parameter("lst", Typed[java.util.List[_]]), Parameter("f", Typed.fromDetailedType[Lambda[_, Boolean]])),
        None,
        Typed.fromDetailedType[java.util.List[_]]
      )
    ))

    override def computeResultType(arguments: List[typing.TypingResult]): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    arguments match {
      case lst :: _ :: Nil => lst.validNel
      case _ => ArgumentTypeError.invalidNel
    }
  }
}
