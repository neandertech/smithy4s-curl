package smithy4s_curl

import scala.quoted.*
import curl.enumerations.CURLcode
import curl.all.curl_easy_strerror
import scalanative.unsafe.fromCString

inline def check(inline expr: => CURLcode): CURLcode = ${ checkImpl('expr) }

private def checkImpl(expr: Expr[CURLcode])(using Quotes): Expr[CURLcode] =
  import quotes.*
  val e = Expr(s"${expr.show} failed: ")

  '{
    val code = $expr
    assert(
      code == CURLcode.CURLE_OK,
      $e + "[" + fromCString(curl_easy_strerror(code)) + "]"
    )
    code
  }
end checkImpl
