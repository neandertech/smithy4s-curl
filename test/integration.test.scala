package smithy4s_curl.tests

import cats.effect.*
import cats.effect.std.Env
import httpbin.*
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s_curl.*

import scala.util.Try

case class Probe(ioClient: HttpBinService[IO], curlClient: HttpBinService[Try])

object IntegrationTest extends weaver.IOSuite:
  val uri =
    Env[IO]
      .get("HTTPBIN_URL")
      .flatMap(
        IO.fromOption(_)(
          RuntimeException(
            "no HTTPBIN_URL env variable set, cannot run integration tests"
          )
        )
      )
      .map(Uri.unsafeFromString)
      .toResource

  override type Res = Probe

  override def sharedResource: Resource[IO, Res] =
    EmberClientBuilder
      .default[IO]
      .build
      .both(uri)
      .flatMap: (client, uri) =>
        val curlClient = SimpleRestJsonCurlClient(
          HttpBinService,
          uri.toString,
          SyncCurlClient()
        ).make
        val ioClientR =
          SimpleRestJsonBuilder(HttpBinService).client(client).uri(uri).resource
        ioClientR.both(Resource.pure(curlClient)).map(Probe.apply)

  test("basics"): probe =>
    for
      io <- probe.ioClient.getIP()
      curl <- IO.fromTry(probe.curlClient.getIP())
    yield expect.same(io, curl)

  test("more complicated"): probe =>
    for
      io <- probe.ioClient.anything(25, Some("yo"), Some("hello"))
      curl <- IO.fromTry(
        probe.curlClient.anything(25, Some("yo"), Some("hello"))
      )
    yield expect.same(io, curl)

end IntegrationTest
