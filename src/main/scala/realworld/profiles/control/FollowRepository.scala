package realworld.profiles.control

import java.util.UUID

import cats.effect.kernel.{Concurrent, Ref, Resource, Sync}
import cats.syntax.all.*
import skunk.codec.all.{bool, uuid}
import skunk.implicits.*
import skunk.{Command, Query, Session, Void}

import realworld.profiles.entity.Follow
import realworld.users.entity.UserId

/** Who follows whom. */
trait FollowRepository[F[_]]:
  def isFollowing(follower: UserId, followed: UserId): F[Boolean]
  def add(follow: Follow): F[Unit]
  def remove(follow: Follow): F[Unit]

object FollowRepository:

  /** In-memory storage, per decision D3. Set semantics give R2.2 and R3.2 their idempotence directly. */
  def inMemory[F[_]: Sync]: F[FollowRepository[F]] =
    Ref
      .of[F, Set[Follow]](Set.empty)
      .map: store =>
        new FollowRepository[F]:
          def isFollowing(follower: UserId, followed: UserId): F[Boolean] =
            store.get.map(_.contains(Follow(follower, followed)))

          def add(follow: Follow): F[Unit] = store.update(_ + follow)

          def remove(follow: Follow): F[Unit] = store.update(_ - follow)

  /** PostgreSQL storage, per decision D11.
    *
    * The composite primary key is the `Set` the in-memory version used: R2.2's repeated follow is a
    * conflict the insert ignores, and R3.2's unfollow of somebody unfollowed deletes no rows and says
    * nothing about it.
    */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): FollowRepository[F] =
    new FollowRepository[F]:
      def isFollowing(follower: UserId, followed: UserId): F[Boolean] =
        pool.use(_.prepare(Exists).flatMap(_.unique((follower.value, followed.value))))

      def add(follow: Follow): F[Unit] = run(Insert, follow)

      def remove(follow: Follow): F[Unit] = run(Delete, follow)

      private def run(command: Command[(UUID, UUID)], follow: Follow): F[Unit] =
        pool.use(_.prepare(command).flatMap(_.execute((follow.follower.value, follow.followed.value)))).void

  /** The tables this component owns.
    *
    * No foreign key to `users`. A reference from this table to that one would be this BC reaching into
    * another's storage, which the architecture forbids for the same reason it forbids the join — `users`
    * is reachable only through its boundary. The cost is that a follow can outlive the account it names;
    * the benefit is that the rule holds in the schema as well as in the code.
    */
  val Tables: List[Command[Void]] =
    List(
      sql"""CREATE TABLE IF NOT EXISTS follows (
              follower uuid NOT NULL,
              followed uuid NOT NULL,
              PRIMARY KEY (follower, followed)
            )""".command
    )

  private val Exists: Query[(UUID, UUID), Boolean] =
    sql"SELECT EXISTS (SELECT 1 FROM follows WHERE follower = $uuid AND followed = $uuid)".query(bool)

  private val Insert: Command[(UUID, UUID)] =
    sql"INSERT INTO follows (follower, followed) VALUES ($uuid, $uuid) ON CONFLICT DO NOTHING".command

  private val Delete: Command[(UUID, UUID)] =
    sql"DELETE FROM follows WHERE follower = $uuid AND followed = $uuid".command
