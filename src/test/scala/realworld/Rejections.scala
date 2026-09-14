package realworld

import cats.effect.IO
import cats.mtl.{Handle, Raise}
import cats.syntax.all.*

/** Runs a boundary operation outside any handling scope, so a row can assert on the rejection itself.
  *
  * Each component's fixture wraps these with its own error type fixed, which keeps inference out of the
  * rows.
  */
object Rejections:

  def attempt[E, A](operation: Raise[IO, E] ?=> IO[A]): IO[Either[E, A]] =
    Handle.allow[E](operation.map(_.asRight[E])).rescue(error => error.asLeft[A].pure[IO])

  def accepted[E, A](operation: Raise[IO, E] ?=> IO[A]): IO[A] =
    attempt[E, A](operation).flatMap:
      _.leftMap(error => AssertionError(s"expected the operation to be accepted, but it raised $error"))
        .liftTo[IO]

  def rejected[E, A](operation: Raise[IO, E] ?=> IO[A]): IO[E] =
    attempt[E, A](operation).flatMap:
      _.swap
        .leftMap(value => AssertionError(s"expected the operation to be rejected, but it returned $value"))
        .liftTo[IO]
