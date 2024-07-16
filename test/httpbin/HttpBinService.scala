package httpbin

import smithy4s.Endpoint
import smithy4s.Hints
import smithy4s.Schema
import smithy4s.Service
import smithy4s.ShapeId
import smithy4s.Transformation
import smithy4s.kinds.PolyFunction5
import smithy4s.kinds.toPolyFunction5.const5
import smithy4s.schema.OperationSchema
import smithy4s.schema.Schema.unit

trait HttpBinServiceGen[F[_, _, _, _, _]] {
  self =>

  def getIP(): F[Unit, Nothing, GetIPOutput, Nothing, Nothing]
  def anything(id: Int, test: Option[String] = None, queryParam: Option[String] = None): F[AnythingInput, Nothing, AnythingOutput, Nothing, Nothing]

  def transform: Transformation.PartiallyApplied[HttpBinServiceGen[F]] = Transformation.of[HttpBinServiceGen[F]](this)
}

object HttpBinServiceGen extends Service.Mixin[HttpBinServiceGen, HttpBinServiceOperation] {

  val id: ShapeId = ShapeId("httpbin", "HttpBinService")
  val version: String = "1.0.0"

  val hints: Hints = Hints(
    alloy.SimpleRestJson(),
  ).lazily

  def apply[F[_]](implicit F: Impl[F]): F.type = F

  object ErrorAware {
    def apply[F[_, _]](implicit F: ErrorAware[F]): F.type = F
    type Default[F[+_, +_]] = Constant[smithy4s.kinds.stubs.Kind2[F]#toKind5]
  }

  val endpoints: Vector[smithy4s.Endpoint[HttpBinServiceOperation, _, _, _, _, _]] = Vector(
    HttpBinServiceOperation.GetIP,
    HttpBinServiceOperation.Anything,
  )

  def input[I, E, O, SI, SO](op: HttpBinServiceOperation[I, E, O, SI, SO]): I = op.input
  def ordinal[I, E, O, SI, SO](op: HttpBinServiceOperation[I, E, O, SI, SO]): Int = op.ordinal
  override def endpoint[I, E, O, SI, SO](op: HttpBinServiceOperation[I, E, O, SI, SO]) = op.endpoint
  class Constant[P[-_, +_, +_, +_, +_]](value: P[Any, Nothing, Nothing, Nothing, Nothing]) extends HttpBinServiceOperation.Transformed[HttpBinServiceOperation, P](reified, const5(value))
  type Default[F[+_]] = Constant[smithy4s.kinds.stubs.Kind1[F]#toKind5]
  def reified: HttpBinServiceGen[HttpBinServiceOperation] = HttpBinServiceOperation.reified
  def mapK5[P[_, _, _, _, _], P1[_, _, _, _, _]](alg: HttpBinServiceGen[P], f: PolyFunction5[P, P1]): HttpBinServiceGen[P1] = new HttpBinServiceOperation.Transformed(alg, f)
  def fromPolyFunction[P[_, _, _, _, _]](f: PolyFunction5[HttpBinServiceOperation, P]): HttpBinServiceGen[P] = new HttpBinServiceOperation.Transformed(reified, f)
  def toPolyFunction[P[_, _, _, _, _]](impl: HttpBinServiceGen[P]): PolyFunction5[HttpBinServiceOperation, P] = HttpBinServiceOperation.toPolyFunction(impl)

}

sealed trait HttpBinServiceOperation[Input, Err, Output, StreamedInput, StreamedOutput] {
  def run[F[_, _, _, _, _]](impl: HttpBinServiceGen[F]): F[Input, Err, Output, StreamedInput, StreamedOutput]
  def ordinal: Int
  def input: Input
  def endpoint: Endpoint[HttpBinServiceOperation, Input, Err, Output, StreamedInput, StreamedOutput]
}

object HttpBinServiceOperation {

  object reified extends HttpBinServiceGen[HttpBinServiceOperation] {
    def getIP(): GetIP = GetIP()
    def anything(id: Int, test: Option[String] = None, queryParam: Option[String] = None): Anything = Anything(AnythingInput(id, test, queryParam))
  }
  class Transformed[P[_, _, _, _, _], P1[_ ,_ ,_ ,_ ,_]](alg: HttpBinServiceGen[P], f: PolyFunction5[P, P1]) extends HttpBinServiceGen[P1] {
    def getIP(): P1[Unit, Nothing, GetIPOutput, Nothing, Nothing] = f[Unit, Nothing, GetIPOutput, Nothing, Nothing](alg.getIP())
    def anything(id: Int, test: Option[String] = None, queryParam: Option[String] = None): P1[AnythingInput, Nothing, AnythingOutput, Nothing, Nothing] = f[AnythingInput, Nothing, AnythingOutput, Nothing, Nothing](alg.anything(id, test, queryParam))
  }

  def toPolyFunction[P[_, _, _, _, _]](impl: HttpBinServiceGen[P]): PolyFunction5[HttpBinServiceOperation, P] = new PolyFunction5[HttpBinServiceOperation, P] {
    def apply[I, E, O, SI, SO](op: HttpBinServiceOperation[I, E, O, SI, SO]): P[I, E, O, SI, SO] = op.run(impl) 
  }
  final case class GetIP() extends HttpBinServiceOperation[Unit, Nothing, GetIPOutput, Nothing, Nothing] {
    def run[F[_, _, _, _, _]](impl: HttpBinServiceGen[F]): F[Unit, Nothing, GetIPOutput, Nothing, Nothing] = impl.getIP()
    def ordinal: Int = 0
    def input: Unit = ()
    def endpoint: smithy4s.Endpoint[HttpBinServiceOperation,Unit, Nothing, GetIPOutput, Nothing, Nothing] = GetIP
  }
  object GetIP extends smithy4s.Endpoint[HttpBinServiceOperation,Unit, Nothing, GetIPOutput, Nothing, Nothing] {
    val schema: OperationSchema[Unit, Nothing, GetIPOutput, Nothing, Nothing] = Schema.operation(ShapeId("httpbin", "GetIP"))
      .withInput(unit)
      .withOutput(GetIPOutput.schema)
      .withHints(smithy.api.Http(method = smithy.api.NonEmptyString("GET"), uri = smithy.api.NonEmptyString("/ip"), code = 200), smithy.api.Readonly())
    def wrap(input: Unit): GetIP = GetIP()
  }
  final case class Anything(input: AnythingInput) extends HttpBinServiceOperation[AnythingInput, Nothing, AnythingOutput, Nothing, Nothing] {
    def run[F[_, _, _, _, _]](impl: HttpBinServiceGen[F]): F[AnythingInput, Nothing, AnythingOutput, Nothing, Nothing] = impl.anything(input.id, input.test, input.queryParam)
    def ordinal: Int = 1
    def endpoint: smithy4s.Endpoint[HttpBinServiceOperation,AnythingInput, Nothing, AnythingOutput, Nothing, Nothing] = Anything
  }
  object Anything extends smithy4s.Endpoint[HttpBinServiceOperation,AnythingInput, Nothing, AnythingOutput, Nothing, Nothing] {
    val schema: OperationSchema[AnythingInput, Nothing, AnythingOutput, Nothing, Nothing] = Schema.operation(ShapeId("httpbin", "Anything"))
      .withInput(AnythingInput.schema)
      .withOutput(AnythingOutput.schema)
      .withHints(smithy.api.Http(method = smithy.api.NonEmptyString("POST"), uri = smithy.api.NonEmptyString("/anything"), code = 200))
    def wrap(input: AnythingInput): Anything = Anything(input)
  }
}

