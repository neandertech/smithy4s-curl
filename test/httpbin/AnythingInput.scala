package httpbin

import smithy4s.Hints
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.ShapeTag
import smithy4s.schema.Schema.int
import smithy4s.schema.Schema.string
import smithy4s.schema.Schema.struct

final case class AnythingInput(id: Int, test: Option[String] = None, queryParam: Option[String] = None)

object AnythingInput extends ShapeTag.Companion[AnythingInput] {
  val id: ShapeId = ShapeId("httpbin", "AnythingInput")

  val hints: Hints = Hints(
    smithy.api.Input(),
  ).lazily

  // constructor using the original order from the spec
  private def make(test: Option[String], id: Int, queryParam: Option[String]): AnythingInput = AnythingInput(id, test, queryParam)

  implicit val schema: Schema[AnythingInput] = struct(
    string.optional[AnythingInput]("test", _.test),
    int.required[AnythingInput]("id", _.id),
    string.optional[AnythingInput]("queryParam", _.queryParam).addHints(smithy.api.HttpQuery("my-q")),
  )(make).withId(id).addHints(hints)
}
