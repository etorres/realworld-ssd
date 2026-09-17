package realworld.support.storage

import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.{Resource, Temporal}
import cats.effect.std.Console
import cats.syntax.all.*
import fs2.io.net.Network
import natchez.Trace.Implicits.noop
import skunk.{Command, Session, Void}

/** Connection pooling and table creation.
  *
  * Declared in `AGENTS.md` as outside the spec surface, on the same grounds as `Main`: it decides how a
  * component reaches its storage, never how a capability behaves.
  */
object Database:

  final case class Settings(
      host: String,
      port: Int,
      user: String,
      password: Option[String],
      database: String,
      poolSize: Int
  )

  def pool[F[_]: Temporal: Network: Console](settings: Settings): Resource[F, Resource[F, Session[F]]] =
    Session.pooled[F](
      host = settings.host,
      port = settings.port,
      user = settings.user,
      database = settings.database,
      password = settings.password,
      max = settings.poolSize
    )

  /** Applies the table definitions the components supply.
    *
    * A business component owns its tables the way it owns its storage algebra, so the statements come
    * from the components themselves rather than from one central script — nothing here knows what a
    * `tags` table looks like.
    */
  def migrate[F[_]: MonadCancelThrow](pool: Resource[F, Session[F]], tables: List[Command[Void]]): F[Unit] =
    pool.use(session => tables.traverse_(session.execute))
