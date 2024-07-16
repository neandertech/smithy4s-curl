package smithy4s_curl

import curl.all.{curl_easy_strerror, curl_url_strerror}
import curl.enumerations.{CURLUcode, CURLcode}

import scala.quoted.*
import scala.util.{Failure, Success, Try}

import scalanative.unsafe.fromCString

case class CurlClientStateException(msg: String) extends Exception

case class CurlException(code: CURLcode, msg: String)
    extends Exception(
      s"Curl error: ${CURLcode.getName(code).getOrElse("")}($code): $msg"
    )

inline def checkTry(inline expr: => CURLcode): Try[CURLcode] = ${
  checkTryImpl('expr)
}

private def checkTryImpl(expr: Expr[CURLcode])(using
    Quotes
): Expr[Try[CURLcode]] =
  '{
    val code = $expr

    if code != CURLcode.CURLE_OK then
      Failure(
        CurlException(
          code,
          fromCString(curl_easy_strerror(code))
        )
      )
    else Success(code)
    end if

  }
end checkTryImpl

inline def check(inline expr: => CURLcode): CURLcode = ${ checkImpl('expr) }

private def checkImpl(expr: Expr[CURLcode])(using Quotes): Expr[CURLcode] =
  '{
    val code = $expr

    if code != CURLcode.CURLE_OK then
      throw new CurlException(
        code,
        fromCString(curl_easy_strerror(code))
      )
    end if

    code
  }
end checkImpl

case class CurlUrlParseException(code: CURLUcode, msg: String)
    extends Exception(
      s"Curl URL parsing error: ${CURLUcode.getName(code).getOrElse("")}($code): $msg"
    )

inline def checkU(inline expr: => CURLUcode): CURLUcode = ${ checkUImpl('expr) }

private def checkUImpl(expr: Expr[CURLUcode])(using Quotes): Expr[CURLUcode] =
  '{
    val code = $expr

    if code != CURLUcode.CURLUE_OK then
      throw new CurlUrlParseException(
        code,
        fromCString(curl_url_strerror(code))
      )
    end if
    code
  }
end checkUImpl
