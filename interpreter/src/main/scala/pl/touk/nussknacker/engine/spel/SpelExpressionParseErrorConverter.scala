package pl.touk.nussknacker.engine.spel

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericFunctionTypingError, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.TypeInfos.MethodInfo
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.{ArgumentTypeError, GenericFunctionError}

case class SpelExpressionParseErrorConverter(methodInfo: MethodInfo, invocationArguments: List[TypingResult]) {
  def convert(error: GenericFunctionTypingError): ExpressionParseError = {
    val givenSignature = Signature(invocationArguments, None)

    error match {
      case GenericFunctionTypingError.ArgumentTypeError =>
        val expectedSignature = Signature(
          methodInfo.staticNoVarArgParameters.map(_.refClazz),
          methodInfo.staticVarArgParameter.map(_.refClazz)
        )
        ArgumentTypeError(methodInfo.name, givenSignature, NonEmptyList.of(expectedSignature))
      case e: GenericFunctionTypingError.ArgumentTypeErrorWithSignatures =>
        ArgumentTypeError(methodInfo.name, givenSignature, e.signatures)
      case e: GenericFunctionTypingError.CustomError =>
        GenericFunctionError(e.message)
    }
  }
}