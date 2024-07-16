package httpbin

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

final case class GetIPOutput(origin: Option[String] = None)

object GetIPOutput extends ShapeTag.Companion[GetIPOutput] {
  val id: ShapeId = ShapeId("httpbin", "GetIPOutput")

  val hints: Hints = Hints(
    smithy.api.Output(),
  ).lazily

  // constructor using the original order from the spec
  private def make(origin: Option[String]): GetIPOutput = GetIPOutput(origin)

  implicit val schema: Schema[GetIPOutput] = struct(
    string.optional[GetIPOutput]("origin", _.origin),
  )(make).withId(id).addHints(hints)
}
