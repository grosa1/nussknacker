package pl.touk.nussknacker.engine.api;

import cats.data.NonEmptyList;
import cats.data.Validated;
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult;
import scala.Tuple2;
import scala.collection.immutable.List;

public interface TypingFunction {
    List<Tuple2<String, TypingResult>> staticParameters();

    TypingResult staticResult();

    Validated<NonEmptyList<String>, TypingResult> apply(List<TypingResult> arguments);
}
