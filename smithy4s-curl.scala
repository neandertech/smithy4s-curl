package smithy4s_curl

import smithy4s.Endpoint.Middleware
import smithy4s.capability.MonadThrowLike
import smithy4s.client.*
import smithy4s.codecs.BlobEncoder
import smithy4s.http.HttpUriScheme.{Http, Https}
import smithy4s.http.{
  CaseInsensitive,
  HttpMethod,
  HttpRequest,
  HttpUnaryClientCodecs,
  Metadata
}
import smithy4s.json.Json
import smithy4s.{Blob, Endpoint}

import smithy4s.http.HttpDiscriminator

import scalanative.unsafe.*
import smithy4s.http.HttpResponse
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import smithy4s.http.HttpUriScheme
import smithy4s.http.HttpUri
import util.chaining.*

import curl.all as C
import scala.collection.mutable.ArrayBuilder
import scala.scalanative.libc.string
import scala.scalanative.unsigned.*

class SyncCurlClient private (
    private var valid: Boolean,
    CURL: Ptr[curl.all.CURL]
) extends AutoCloseable:
  override def close(): Unit =
    C.curl_easy_cleanup(CURL); valid = false

  import curl.all.CURLoption.*
  def send(request: smithy4s.http.HttpRequest[Blob]): Try[HttpResponse[Blob]] =
    assert(valid, "This client has already been shut down and cannot be used!")

    val finalizers = Seq.newBuilder[() => Unit]

    finalizers += (() => C.curl_easy_reset(CURL))

    Zone:
      implicit z =>
        try
          for
            _ <- setMethod(request)
            _ <- setURL(request)

            (headerSetResult, finalizer) = setHeaders(CURL, request.headers)
            _ = finalizers.addOne(finalize)
            _ <- headerSetResult

            _ <- setBody(request)

            _ <- Try(
              check(
                OPT(C.CURLoption.CURLOPT_WRITEFUNCTION, readResponseCallback)
              )
            )
            _ <- Try(
              check(
                OPT(C.CURLoption.CURLOPT_HEADERFUNCTION, writeHeadersCallback)
              )
            )

            bodyBuilder = Array.newBuilder[Byte]
            (bodyBuilderPtr, deallocate) = Captured.unsafe(bodyBuilder)
            _ = finalizers += deallocate
            _ <- Try(
              check(OPT(C.CURLoption.CURLOPT_WRITEDATA, bodyBuilderPtr))
            )

            headerBuilder = Array.newBuilder[Byte]
            (headerBuilderPtr, deallocate) = Captured.unsafe(headerBuilder)
            _ = finalizers += deallocate
            _ <- Try(
              check(OPT(C.CURLoption.CURLOPT_HEADERDATA, headerBuilderPtr))
            )

            _ <- Try(check(curl.all.curl_easy_perform(CURL)))

            headerLines = new String(
              headerBuilder.result()
            ).linesIterator.toList

            headers = headerLines.flatMap(parseHeaders).groupMap(_._1)(_._2)

            code <- getCode()
          yield HttpResponse(
            code,
            headers,
            Blob.apply(bodyBuilder.result().tap(arr => new String(arr)))
          )
        finally
          finalizers.result().foreach { fin =>
            fin()
          }
  end send

  private def parseHeaders(str: String): Seq[(CaseInsensitive, String)] =
    val array = str
      .split("\n")
      .filter(_.trim.length > 0)
    Seq(array*)
      .map { line =>
        val split = line.split(":", 2)
        if split.size == 2 then CaseInsensitive(split(0).trim) -> split(1).trim
        else CaseInsensitive(split(0).trim) -> ""
      }
  end parseHeaders

  private val readResponseCallback =
    CFuncPtr4.fromScalaFunction {
      (ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: Ptr[Byte]) =>
        val vec = !userdata.asInstanceOf[Ptr[ArrayBuilder[Byte]]]

        val newArr = new Array[Byte](nmemb.toInt)

        string.memcpy(newArr.at(0), ptr, nmemb)

        vec.addAll(newArr)

        nmemb * size
    }

  private val writeHeadersCallback = CFuncPtr4.fromScalaFunction {
    (
        buffer: Ptr[Byte],
        size: CSize,
        nitems: CSize,
        userdata: Ptr[Byte]
    ) =>
      val vec = !userdata.asInstanceOf[Ptr[ArrayBuilder[Byte]]]

      for i <- 0 until nitems.toInt do vec.addOne(buffer(i))

      nitems * size

  }

  private def getCode() =
    Try {
      val code = stackalloc[Int]()
      check(C.curl_easy_getinfo(CURL, C.CURLINFO.CURLINFO_RESPONSE_CODE, code))
      !code
    }

  private def setBody(request: HttpRequest[Blob])(using Zone) =
    if !request.body.isEmpty then
      val ar = Array.ofDim[Byte](request.body.size + 1)

      request.body.copyToArray(ar, 0, 0, request.body.size)

      ar(request.body.size) = 0

      Try(check(OPT(C.CURLoption.CURLOPT_POSTFIELDS, ar.atUnsafe(0))))
    else Success(())
  end setBody

  private def setURL(request: HttpRequest[Blob])(using Zone): Try[Unit] =
    Try(
      OPT(
        CURLOPT_URL,
        toCString(fromSmithy4sHttpUri(request.uri).tap(u => s"Url: $u"))
      )
    )

  private def makeHeaders(hd: Map[CaseInsensitive, Seq[String]])(using Zone) =
    var slist: Ptr[C.curl_slist] = null
    hd.foreach { case (headerName, headerValues) =>
      headerValues.foreach { headerValue =>
        slist =
          C.curl_slist_append(slist, toCString(s"$headerName:$headerValue"))
      }
    }
    slist
  end makeHeaders

  private def setHeaders(
      handle: Ptr[C.CURL],
      hd: Map[CaseInsensitive, Seq[String]]
  )(using
      Zone
  ) =
    val slist = makeHeaders(hd)
    (
      Try(
        check(
          C.curl_easy_setopt(handle, C.CURLoption.CURLOPT_HTTPHEADER, slist)
        )
      ),
      () => C.curl_slist_free_all(slist)
    )
  end setHeaders

  private def setMethod(request: HttpRequest[Blob])(using Zone): Try[Unit] =
    Try:
      request.method match
        case HttpMethod.GET => OPT(CURLOPT_HTTPGET, 1L)
        // TODO: check
        case HttpMethod.POST   => OPT(CURLOPT_POST, 1L)
        case HttpMethod.PUT    => OPT(CURLOPT_PUT, 1L)
        case HttpMethod.DELETE => OPT(CURLOPT_CUSTOMREQUEST, c"DELETE")
        case HttpMethod.PATCH  => OPT(CURLOPT_CUSTOMREQUEST, c"PATCH")
        case HttpMethod.OTHER(m) if m.equalsIgnoreCase("HEAD") =>
          OPT(CURLOPT_HTTPHEADER, 1L)
        case HttpMethod.OTHER(m) if m.equalsIgnoreCase("OPTIONS") =>
          OPT(CURLOPT_RTSP_REQUEST, 1L)
        case HttpMethod.OTHER(m) if m.equalsIgnoreCase("CONNECT") =>
          OPT(CURLOPT_CONNECT_ONLY, 1L)
        case HttpMethod.OTHER(m) => OPT(CURLOPT_CUSTOMREQUEST, toCString(m))

  private inline def OPT[T](opt: curl.all.CURLoption, value: T) =
    check(curl.all.curl_easy_setopt(CURL, opt, value))

  def fromSmithy4sHttpUri(uri: smithy4s.http.HttpUri): String =
    val qp = uri.queryParams
    val newValue =
      uri.scheme match
        case Http  => "http"
        case Https => "https"
    val hostName = uri.host
    val port =
      uri.port
        .filterNot(p => uri.host.endsWith(s":$p"))
        .map(":" + _.toString)
        .getOrElse("")

    val path = "/" + uri.path.mkString("/")
    val query =
      if qp.isEmpty then ""
      else
        var b = "?"
        qp.zipWithIndex.map:
          case ((key, values), idx) =>
            if idx != 0 then b += "&"
            b += key
            for
              i <- 0 until values.length
              value = values(i)
            do
              if i == 0 then b += "=" + value
              else b += s"&$key=$value"
            end for

        b

    s"$newValue://$hostName$port$path$query"
  end fromSmithy4sHttpUri
end SyncCurlClient

object SyncCurlClient:
  def apply() = new SyncCurlClient(valid = true, curl.all.curl_easy_init())

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

private[smithy4s_curl] object SimpleRestJsonCodecs
    extends SimpleRestJsonCodecs(1024, false, false)

private[smithy4s_curl] case class SimpleRestJsonCodecs(
    maxArity: Int,
    explicitDefaultsEncoding: Boolean,
    hostPrefixInjection: Boolean
):
  private val hintMask =
    alloy.SimpleRestJson.protocol.hintMask

  object StackZone extends Zone:

    override inline def alloc(size: CSize): Ptr[Byte] = stackalloc[Byte](size)

    override def close(): Unit = ()

    override def isClosed: Boolean = false
  end StackZone

  def toSmithy4sHttpUri(
      uri: String,
      pathParams: Option[smithy4s.http.PathParams] = None
  ): smithy4s.http.HttpUri =

    import C.CURLUPart.*
    import C.CURLUcode.*

    Zone:
      implicit z =>

        val url = C.curl_url()

        checkU(
          C.curl_url_set(
            url,
            CURLUPART_URL,
            toCString(uri),
            0.toUInt
          )
        )

        def getPart(part: C.CURLUPart): String =
          val scheme = stackalloc[Ptr[Byte]](1)

          checkU(C.curl_url_get(url, part, scheme, 0.toUInt))

          val str = fromCString(!scheme)

          C.curl_free(!scheme)

          str
        end getPart

        val httpScheme = getPart(CURLUPART_SCHEME) match
          case "https" => HttpUriScheme.Https
          case "http"  => HttpUriScheme.Http
          case other =>
            throw UnsupportedOperationException(
              s"Protocol `${other}` is not supported"
            )

        val port = Try(getPart(CURLUPART_PORT)) match
          case Failure(CurlUrlParseException(CURLUE_NO_PORT, _)) =>
            None
          case Success(value) => Some(value.toInt)

          case Failure(other) => throw other

        val host = getPart(CURLUPART_HOST)
        val path = getPart(CURLUPART_PATH).split("/").dropWhile(_.isEmpty())

        HttpUri(
          httpScheme,
          host,
          port,
          path,
          Map.empty,
          pathParams
        )
  end toSmithy4sHttpUri

  val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter
        .withHintMask(hintMask)
        .withMaxArity(maxArity)
        .withExplicitDefaultsEncoding(explicitNulls = true)
    )

  val payloadEncoders: BlobEncoder.Compiler =
    jsonCodecs.encoders

  val payloadDecoders =
    jsonCodecs.decoders

  val errorHeaders = List(
    smithy4s.http.errorTypeHeader
  )

  def makeClientCodecs(
      uri: String
  ): UnaryClientCodecs.Make[Try, HttpRequest[Blob], HttpResponse[Blob]] =
    val baseRequest = HttpRequest(
      HttpMethod.POST,
      toSmithy4sHttpUri(uri, None),
      Map.empty,
      Blob.empty
    )

    HttpUnaryClientCodecs.builder
      .withBodyEncoders(payloadEncoders)
      .withSuccessBodyDecoders(payloadDecoders)
      .withErrorBodyDecoders(payloadDecoders)
      .withErrorDiscriminator(resp =>
        Success(HttpDiscriminator.fromResponse(errorHeaders, resp))
      )
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(
        Metadata.Encoder.withExplicitDefaultsEncoding(
          explicitDefaultsEncoding
        )
      )
      .withBaseRequest(_ => Success(baseRequest))
      .withRequestMediaType("application/json")
      .withRequestTransformation[HttpRequest[Blob]](Success(_))
      .withResponseTransformation[HttpResponse[Blob]](Success(_))
      .withHostPrefixInjection(hostPrefixInjection)
      .build()
  end makeClientCodecs
end SimpleRestJsonCodecs

given MonadThrowLike[Try] with
  def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] = fa.flatMap(f)
  def handleErrorWith[A](fa: Try[A])(f: Throwable => Try[A]): Try[A] =
    fa match
      case Failure(exception) => f(exception)
      case _                  => fa

  def pure[A](a: A): Try[A] = Success(a)
  def raiseError[A](e: Throwable): Try[A] = Failure(e)
  def zipMapAll[A](seq: IndexedSeq[Try[Any]])(f: IndexedSeq[Any] => A): Try[A] =
    val b = IndexedSeq.newBuilder[Any]
    b.sizeHint(seq.size)
    var failure: Throwable = null

    var i = 0

    while failure == null && i < seq.length do
      seq(i) match
        case Failure(exception) => failure = exception
        case Success(value)     => if failure == null then b += value

      i += 1
    end while

    if failure != null then Failure(failure) else Try(f(b.result()))
  end zipMapAll
end given

import smithy4s_curl.*
import httpbin.*

@main def hello =
  val smithyClient = SimpleRestJsonCurlClient(
    HttpBinService,
    "https://httpbin.org",
    SyncCurlClient()
  ).make

  println(smithyClient.anything(25, Some("This is a test!")))
end hello
