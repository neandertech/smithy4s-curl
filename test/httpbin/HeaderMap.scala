package httpbin

import smithy4s.Hints
import smithy4s.Newtype
import smithy4s.Schema
import smithy4s.ShapeId
import smithy4s.schema.Schema.bijection
import smithy4s.schema.Schema.map
import smithy4s.schema.Schema.string

object HeaderMap extends Newtype[Map[String, String]] {
  val id: ShapeId = ShapeId("httpbin", "HeaderMap")
  val hints: Hints = Hints.empty
  val underlyingSchema: Schema[Map[String, String]] = map(string, string).withId(id).addHints(hints)
  implicit val schema: Schema[HeaderMap] = bijection(underlyingSchema, asBijection)
}
