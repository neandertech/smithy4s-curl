package smithy4s_curl

import curl.all as C
import smithy4s.Blob
import smithy4s.client.*
import smithy4s.codecs.BlobEncoder
import smithy4s.http.HttpUriScheme.{Http, Https}
import smithy4s.http.{
  HttpDiscriminator,
  HttpMethod,
  HttpRequest,
  HttpResponse,
  HttpUnaryClientCodecs,
  HttpUri,
  HttpUriScheme,
  Metadata
}
import smithy4s.json.Json
import smithy4s_curl.*

import scala.scalanative.unsigned.*
import scala.util.{Failure, Success, Try}

import scalanative.unsafe.*
import util.chaining.*

private[smithy4s_curl] object SimpleRestJsonCodecs
    extends SimpleRestJsonCodecs(1024, false, false)

private[smithy4s_curl] case class SimpleRestJsonCodecs(
    maxArity: Int,
    explicitDefaultsEncoding: Boolean,
    hostPrefixInjection: Boolean
):
  private val hintMask =
    alloy.SimpleRestJson.protocol.hintMask

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
        val path = getPart(CURLUPART_PATH)

        val cleanedPath: IndexedSeq[String] =
          path.tail
            // drop the guaranteed leading slash, so that we don't produce an empty segment for it
            .tail
            // splitting an empty path would produce a single element, so we special-case to empty
            .match
              case ""    => IndexedSeq.empty[String]
              case other => other.split("/")

        HttpUri(
          httpScheme,
          host,
          port,
          cleanedPath,
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
