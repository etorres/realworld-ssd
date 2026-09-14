package realworld.articles.control

import cats.effect.kernel.{Ref, Sync}
import cats.syntax.all.*

import realworld.articles.entity.{ArticleId, Favorite}

/** Who has favorited what. Counting and flag-setting belong to the service. */
trait FavoriteRepository[F[_]]:
  def add(favorite: Favorite): F[Unit]
  def remove(favorite: Favorite): F[Unit]

  /** R6.1 — a deleted article takes its favorites with it. */
  def removeAllOf(article: ArticleId): F[Unit]

  def all: F[Set[Favorite]]

object FavoriteRepository:

  /** In-memory storage, per decision D3. Set semantics give R7.2 and R8.2 their idempotence directly. */
  def inMemory[F[_]: Sync]: F[FavoriteRepository[F]] =
    Ref
      .of[F, Set[Favorite]](Set.empty)
      .map: store =>
        new FavoriteRepository[F]:
          def add(favorite: Favorite): F[Unit] = store.update(_ + favorite)

          def remove(favorite: Favorite): F[Unit] = store.update(_ - favorite)

          def removeAllOf(article: ArticleId): F[Unit] = store.update(_.filterNot(_.article == article))

          def all: F[Set[Favorite]] = store.get
