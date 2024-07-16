package smithy4s_curl.tests

import smithy4s.http.HttpUriScheme
import smithy4s.http.HttpUri

object UnitTest extends weaver.FunSuiteIO:
  val uri =
    smithy4s.http.HttpUri(
      scheme = HttpUriScheme.Https,
      path = Vector("hello", "world"),
      queryParams = Map(
        "k" -> Seq.empty,
        "k2" -> Seq("hello"),
        "k3" -> Seq("hello", "world", "!")
      ),
      host = "localhost",
      pathParams = None,
      port = Some(9999)
    )

  def enc(uri: HttpUri): String =
    smithy4s_curl.SimpleRestJsonCodecs.fromSmithy4sHttpUri(uri)

  test("URI encoding"):
    expect.same(
      enc(uri),
      "https://localhost:9999/hello/world?k&k2=hello&k3=hello&k3=world&k3=!"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty)),
      "https://localhost:9999/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, scheme = HttpUriScheme.Http)),
      "http://localhost:9999/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, host = "hello.com")),
      "https://hello.com:9999/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, port = None)),
      "https://localhost/hello/world"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, path = Vector.empty)),
      "https://localhost:9999/"
    ) &&
    expect.same(
      enc(uri.copy(queryParams = Map.empty, path = Vector("1", "2", "3"))),
      "https://localhost:9999/1/2/3"
    )
end UnitTest
