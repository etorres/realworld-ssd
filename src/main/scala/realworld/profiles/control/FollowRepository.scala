package realworld.profiles.control

import cats.effect.kernel.{Ref, Sync}
import cats.syntax.all.*

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
