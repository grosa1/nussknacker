package pl.touk.nussknacker.engine.management.sample.global

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, GenericType, NoVarArgSignature, ExpressionParseError, TypingFunction}
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

object GenericHelperFunction {
  @Documentation(description = "extracts type of given object")
  @GenericType(typingFunction = classOf[ExtractTypeHelper])
  def extractType(argument: AnyRef): AnyRef = argument match {
    case _: Integer => (new ExtractTypeHelper).IntOK
    case _: String => (new ExtractTypeHelper).StringOK
    case _ => throw new AssertionError("method called with argument that should cause validation error")
  }

  private class ExtractTypeHelper extends TypingFunction {
    val IntOK = "OK: Int"
    val StringOK = "OK: String"

    private def error(arguments: List[TypingResult]): ExpressionParseError =
      new ArgumentTypeError(
        new NoVarArgSignature("extractType", arguments),
        List(new NoVarArgSignature("extractType", List(Typed(Typed[Int], Typed[String]))))
      )

    override def staticParameters(): Option[List[(String, TypingResult)]] =
      Some(List(("example of desired type", Typed(Typed[Int], Typed[String]))))

    override def staticResult(): Option[TypingResult] =
      Some(Typed(Typed.fromInstance(IntOK), Typed.fromInstance(StringOK)))

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
      case x :: Nil if x.canBeSubclassOf(Typed[Int]) => Typed.fromInstance(IntOK).validNel
      case x :: Nil if x.canBeSubclassOf(Typed[String]) => Typed.fromInstance(StringOK).validNel
      case _ => error(arguments).invalidNel
    }
  }

  @Documentation(description = "generic head function")
  @GenericType(typingFunction = classOf[HeadHelper])
  def head[T >: Null](list: java.util.List[T]): T =
    list.asScala.headOption.orNull

  private class HeadHelper extends TypingFunction {
    private val listClass = classOf[java.util.List[_]]

    private def error(arguments: List[TypingResult]) = {
      new ArgumentTypeError(
        new NoVarArgSignature("head", arguments),
        List(new NoVarArgSignature("head", List(Typed.fromDetailedType[java.util.List[Object]])))
      )
    }

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
      case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
      case TypedClass(`listClass`, _) :: Nil => throw new AssertionError("Lists must have one parameter")
      case _ => error(arguments).invalidNel
    }
  }
}
