package smithy4s_curl

import curl.all as C
import smithy4s.Blob
import smithy4s.http.{CaseInsensitive, HttpMethod, HttpRequest, HttpResponse}
import smithy4s_curl.*

import scala.collection.mutable.ArrayBuilder
import scala.scalanative.libc.string
import scala.scalanative.unsigned.*
import scala.util.{Failure, Success, Try}

import scalanative.unsafe.*
import util.chaining.*

class SyncCurlClient private (
    private var valid: Boolean,
    CURL: Ptr[curl.all.CURL],
    codecs: SimpleRestJsonCodecs
) extends AutoCloseable:
  override def close(): Unit =
    C.curl_easy_cleanup(CURL)
    valid = false

  import curl.all.CURLoption.*
  def send(request: smithy4s.http.HttpRequest[Blob]): Try[HttpResponse[Blob]] =
    if !valid then
      Failure(
        CurlClientStateException(
          "This client has already been shut down and cannot be used!"
        )
      )
    else
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

              _ <- checkTry(
                OPT(
                  C.CURLoption.CURLOPT_WRITEFUNCTION,
                  readResponseCallback
                )
              )
              _ <- checkTry(
                OPT(
                  C.CURLoption.CURLOPT_HEADERFUNCTION,
                  writeHeadersCallback
                )
              )

              bodyBuilder = Array.newBuilder[Byte]
              (bodyBuilderPtr, deallocate) = Captured.unsafe(bodyBuilder)
              _ = finalizers += deallocate

              _ <- checkTry(OPT(C.CURLoption.CURLOPT_WRITEDATA, bodyBuilderPtr))

              headerBuilder = Array.newBuilder[Byte]
              (headerBuilderPtr, deallocate) = Captured.unsafe(headerBuilder)
              _ = finalizers += deallocate

              _ <- checkTry(
                OPT(C.CURLoption.CURLOPT_HEADERDATA, headerBuilderPtr)
              )

              _ <- checkTry(curl.all.curl_easy_perform(CURL))

              headerLines = new String(
                headerBuilder.result()
              ).linesIterator.toList

              headers = headerLines.flatMap(parseHeaders).groupMap(_._1)(_._2)

              code <- getStatusCode()
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

  private def getStatusCode(): Try[Int] =
    val code = stackalloc[Int]()
    checkTry(C.curl_easy_getinfo(CURL, C.CURLINFO.CURLINFO_RESPONSE_CODE, code))
      .map(_ => !code)

  private def setBody(request: HttpRequest[Blob])(using Zone) =
    if !request.body.isEmpty then
      val ar = Array.ofDim[Byte](request.body.size + 1)

      request.body.copyToArray(ar, 0, 0, request.body.size)

      ar(request.body.size) = 0

      checkTry(OPT(C.CURLoption.CURLOPT_POSTFIELDS, ar.atUnsafe(0)))
    else Success(())
  end setBody

  private def setURL(request: HttpRequest[Blob])(using Zone): Try[Unit] =
    Try(
      OPT(
        CURLOPT_URL,
        toCString(codecs.fromSmithy4sHttpUri(request.uri).tap(u => s"Url: $u"))
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
      checkTry(
        C.curl_easy_setopt(handle, C.CURLoption.CURLOPT_HTTPHEADER, slist)
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
end SyncCurlClient

object SyncCurlClient:
  def apply(): SyncCurlClient = new SyncCurlClient(
    valid = true,
    CURL = curl.all.curl_easy_init(),
    codecs = SimpleRestJsonCodecs
  )
end SyncCurlClient
