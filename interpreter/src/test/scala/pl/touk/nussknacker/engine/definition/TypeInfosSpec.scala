package pl.touk.nussknacker.engine.definition

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericFunctionTypingError, Parameter, MethodTypeInfo, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfos.{FunctionalMethodInfo, MethodInfo, StaticMethodInfo}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ArgumentTypeError

class TypeInfosSpec extends FunSuite with Matchers {
  private val noVarArgsMethodInfo =
    StaticMethodInfo(MethodTypeInfo(List(Parameter("", Typed[Int]), Parameter("", Typed[String])), None, Typed[Double]), "f", None)
  private val varArgsMethodInfo =
    StaticMethodInfo(MethodTypeInfo(List(Parameter("", Typed[String])), Some(Parameter("", Typed[Int])), Typed[Float]), "f", None)
  private val superclassMethodInfo =
    StaticMethodInfo(MethodTypeInfo(List(Parameter("", Unknown)), Some(Parameter("", Typed[Number])), Typed[String]), "f", None)

  private def checkApply(info: MethodInfo,
                         args: List[TypingResult],
                         expected: ValidatedNel[String, TypingResult]): Unit =
    info.computeResultType(args).leftMap(_.map(_.message)) shouldBe expected

  private def checkApplyValid(info: MethodInfo,
                              args: List[TypingResult],
                              expected: TypingResult): Unit =
    checkApply(info, args, expected.validNel)

  private def checkApplyInvalid(info: MethodInfo,
                                args: List[TypingResult],
                                expected: ExpressionParseError): Unit =
    checkApply(info, args, expected.message.invalidNel)

  test("should generate type functions for methods without varArgs") {
    def noVarArgsCheckValid(args: List[TypingResult]): Unit =
      checkApplyValid(noVarArgsMethodInfo, args, Typed[Double])
    def noVarArgsCheckInvalid(args: List[TypingResult]): Unit =
      checkApplyInvalid(noVarArgsMethodInfo, args, ArgumentTypeError(
        noVarArgsMethodInfo.name,
        Signature(args, None),
        NonEmptyList.one(Signature(noVarArgsMethodInfo.mainSignature.noVarArgs.map(_.refClazz), None))
      ))

    noVarArgsCheckValid(List(Typed[Int], Typed[String]))

    noVarArgsCheckInvalid(List())
    noVarArgsCheckInvalid(List(Typed[Int], Typed[Double]))
    noVarArgsCheckInvalid(List(Typed[String], Typed[Double]))
    noVarArgsCheckInvalid(List(Typed[Int], Typed[String], Typed[Double]))
  }

  test("should generate type functions for methods with varArgs") {
    def varArgsCheckValid(args: List[TypingResult]): Unit =
      checkApplyValid(varArgsMethodInfo, args, Typed[Float])
    def varArgsCheckInvalid(args: List[TypingResult]): Unit =
      checkApplyInvalid(varArgsMethodInfo, args, ArgumentTypeError(
        varArgsMethodInfo.name,
        Signature(args, None),
        NonEmptyList.one(Signature(
          varArgsMethodInfo.mainSignature.noVarArgs.map(_.refClazz),
          varArgsMethodInfo.mainSignature.varArg.map(_.refClazz)
        ))
      ))

    varArgsCheckValid(List(Typed[String]))
    varArgsCheckValid(List(Typed[String], Typed[Int]))
    varArgsCheckValid(List(Typed[String], Typed[Int], Typed[Int], Typed[Int]))

    varArgsCheckInvalid(List())
    varArgsCheckInvalid(List(Typed[Int]))
    varArgsCheckInvalid(List(Typed[String], Typed[String]))
    varArgsCheckInvalid(List(Typed[String], Typed[Int], Typed[Double]))
    varArgsCheckInvalid(List(Typed[Int], Typed[Int]))
  }

  test("should accept subclasses as arguments to methods") {
    checkApplyValid(superclassMethodInfo, List(Typed[String], Typed[Int], Typed[Double], Typed[Number]), Typed[String])
  }

  test("should automatically validate arguments of generic functions") {
    def f(lst: List[TypingResult]): ValidatedNel[GenericFunctionTypingError, TypingResult] = Typed[Int].validNel

    val methodInfo = FunctionalMethodInfo(
      x => f(x),
      MethodTypeInfo(Parameter("a", Typed[Int]) :: Parameter("b", Typed[Double]) :: Nil, Some(Parameter("c", Typed[String])), Typed[Int]),
      "f",
      None
    )

    methodInfo.computeResultType(List(Typed[Int], Typed[Double])) should be valid;
    methodInfo.computeResultType(List(Typed[Int], Typed[Double], Typed[String])) should be valid;
    methodInfo.computeResultType(List(Typed[Int], Typed[Double], Typed[String], Typed[String])) should be valid

    methodInfo.computeResultType(List(Typed[Int])) should be invalid;
    methodInfo.computeResultType(List(Typed[Int], Typed[String])) should be invalid;
    methodInfo.computeResultType(List(Typed[Double], Typed[Int], Typed[String])) should be invalid;
    methodInfo.computeResultType(List()) should be invalid
  }
}
