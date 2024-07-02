package smithy4s_curl

import scala.quoted.*
import curl.enumerations.CURLcode
import curl.all.curl_easy_strerror
import scalanative.unsafe.fromCString
import curl.enumerations.CURLUcode
import curl.all.curl_url_strerror

case class CurlException(code: CURLcode, msg: String)
    extends Exception(
      s"Curl error: ${CURLcode.getName(code).getOrElse("")}($code): $msg"
    )

inline def check(inline expr: => CURLcode): CURLcode = ${ checkImpl('expr) }

private def checkImpl(expr: Expr[CURLcode])(using Quotes): Expr[CURLcode] =
  import quotes.*

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
  import quotes.*
  val e = Expr(s"${expr.show} failed: ")

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
