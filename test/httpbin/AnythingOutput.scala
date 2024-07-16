package httpbin

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

final case class AnythingOutput(origin: Option[String] = None, method: Option[String] = None, data: Option[String] = None, url: Option[String] = None)

object AnythingOutput extends ShapeTag.Companion[AnythingOutput] {
  val id: ShapeId = ShapeId("httpbin", "AnythingOutput")

  val hints: Hints = Hints(
    smithy.api.Output(),
  ).lazily

  // constructor using the original order from the spec
  private def make(origin: Option[String], method: Option[String], data: Option[String], url: Option[String]): AnythingOutput = AnythingOutput(origin, method, data, url)

  implicit val schema: Schema[AnythingOutput] = struct(
    string.optional[AnythingOutput]("origin", _.origin),
    string.optional[AnythingOutput]("method", _.method),
    string.optional[AnythingOutput]("data", _.data),
    string.optional[AnythingOutput]("url", _.url),
  )(make).withId(id).addHints(hints)
}
