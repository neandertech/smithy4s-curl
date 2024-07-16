package object httpbin {
  type HttpBinService[F[_]] = smithy4s.kinds.FunctorAlgebra[HttpBinServiceGen, F]
  val HttpBinService = HttpBinServiceGen

  type HeaderMap = httpbin.HeaderMap.Type

}