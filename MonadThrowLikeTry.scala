package smithy4s_curl

import smithy4s.capability.MonadThrowLike
import scala.util.*

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
