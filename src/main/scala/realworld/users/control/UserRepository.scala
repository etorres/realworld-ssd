package realworld.users.control

import cats.effect.kernel.{Ref, Sync}
import cats.syntax.all.*

import realworld.users.entity.*

/** Storage for accounts. Uniqueness is not enforced here: the service checks it so that it can name the
  * offending field (R1.2, R1.3, R4.2, R4.3).
  */
trait UserRepository[F[_]]:
  def findById(id: UserId): F[Option[User]]
  def findByEmail(email: Email): F[Option[User]]
  def findByUsername(username: Username): F[Option[User]]
  def save(user: User): F[Unit]

object UserRepository:

  /** In-memory storage, per decision D3 in the system doc. */
  def inMemory[F[_]: Sync]: F[UserRepository[F]] =
    Ref
      .of[F, Map[UserId, User]](Map.empty)
      .map: store =>
        new UserRepository[F]:
          def findById(id: UserId): F[Option[User]] = store.get.map(_.get(id))

          def findByEmail(email: Email): F[Option[User]] = store.get.map(_.values.find(_.email == email))

          def findByUsername(username: Username): F[Option[User]] =
            store.get.map(_.values.find(_.username == username))

          def save(user: User): F[Unit] = store.update(_.updated(user.id, user))
