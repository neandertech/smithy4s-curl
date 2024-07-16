package smithy4s_curl

import smithy4s.Endpoint.Middleware
import smithy4s.client.*
import smithy4s.http.{HttpRequest, HttpResponse}
import smithy4s.{Blob, Endpoint}

import scala.util.Try

class SimpleRestJsonCurlClient[
    Alg[_[_, _, _, _, _]]
] private[smithy4s_curl] (
    service: smithy4s.Service[Alg],
    uri: String,
    client: SyncCurlClient,
    middleware: Endpoint.Middleware[SyncCurlClient],
    codecs: SimpleRestJsonCodecs
):

  def withMaxArity(maxArity: Int): SimpleRestJsonCurlClient[Alg] =
    changeCodecs(_.copy(maxArity = maxArity))

  def withExplicitDefaultsEncoding(
      explicitDefaultsEncoding: Boolean
  ): SimpleRestJsonCurlClient[Alg] =
    changeCodecs(_.copy(explicitDefaultsEncoding = explicitDefaultsEncoding))

  def withHostPrefixInjection(
      hostPrefixInjection: Boolean
  ): SimpleRestJsonCurlClient[Alg] =
    changeCodecs(_.copy(hostPrefixInjection = hostPrefixInjection))

  def make: Alg[[I, E, O, SI, SO] =>> Try[O]] =
    service.impl[Try](
      UnaryClientCompiler[
        Alg,
        Try,
        SyncCurlClient,
        smithy4s.http.HttpRequest[Blob],
        smithy4s.http.HttpResponse[Blob]
      ](
        service = service,
        toSmithy4sClient = SimpleRestJsonCurlClient.lowLevelClient(_),
        client = client,
        middleware = middleware,
        makeClientCodecs = codecs.makeClientCodecs(uri),
        isSuccessful = _.isSuccessful
      )
    )

  private def changeCodecs(
      f: SimpleRestJsonCodecs => SimpleRestJsonCodecs
  ): SimpleRestJsonCurlClient[Alg] =
    new SimpleRestJsonCurlClient(
      service,
      uri,
      client,
      middleware,
      f(codecs)
    )
end SimpleRestJsonCurlClient

object SimpleRestJsonCurlClient:

  def apply[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg],
      url: String,
      client: SyncCurlClient
  ) =
    new SimpleRestJsonCurlClient(
      service = service,
      uri = url,
      client = client,
      codecs = SimpleRestJsonCodecs,
      middleware = Endpoint.Middleware.noop
    )

  private def lowLevelClient(fetch: SyncCurlClient) =
    new UnaryLowLevelClient[Try, HttpRequest[Blob], HttpResponse[Blob]]:
      override def run[Output](request: HttpRequest[Blob])(
          responseCB: HttpResponse[Blob] => Try[Output]
      ): Try[Output] =
        fetch.send(request).flatMap(responseCB)
end SimpleRestJsonCurlClient
