package pl.touk.nussknacker.engine.graph

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.api.CirceUtil._

@ConfiguredJsonCodec sealed abstract class EdgeType
object EdgeType {
  sealed trait FilterEdge extends EdgeType
  sealed trait SwitchEdge extends EdgeType
  case object FilterTrue extends FilterEdge
  case object FilterFalse extends FilterEdge
  case class NextSwitch(condition: Expression) extends SwitchEdge
  case object SwitchDefault extends SwitchEdge
  case class SubprocessOutput(name: String) extends EdgeType
}