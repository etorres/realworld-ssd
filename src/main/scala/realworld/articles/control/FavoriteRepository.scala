package realworld.articles.control

import cats.effect.kernel.{Concurrent, Ref, Resource, Sync}
import cats.syntax.all.*
import skunk.codec.all.{int8, uuid}
import skunk.implicits.*
import skunk.{Codec, Command, Query, Session, Void}

import realworld.articles.entity.{ArticleId, Favorite}
import realworld.users.entity.UserId

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

  /** PostgreSQL storage, per decision D11. The composite primary key is the `Set`: favoriting twice is a
    * conflict the insert ignores (R7.2), and unfavoriting what was never favorited deletes nothing (R8.2).
    */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): FavoriteRepository[F] =
    new FavoriteRepository[F]:
      def add(favorite: Favorite): F[Unit] = run(Insert, favorite)

      def remove(favorite: Favorite): F[Unit] = run(Delete, favorite)

      def removeAllOf(article: ArticleId): F[Unit] =
        pool.use(_.prepare(DeleteAllOf).flatMap(_.execute(article.value))).void

      def all: F[Set[Favorite]] = pool.use(_.execute(All)).map(_.toSet)

      private def run(command: Command[Favorite], favorite: Favorite): F[Unit] =
        pool.use(_.prepare(command).flatMap(_.execute(favorite))).void

  /** The tables this component owns. `favoriter` rather than `user`, which is reserved. */
  val Tables: List[Command[Void]] =
    List(
      sql"""CREATE TABLE IF NOT EXISTS favorites (
              favoriter uuid NOT NULL,
              article   bigint NOT NULL,
              PRIMARY KEY (favoriter, article)
            )""".command
    )

  private val favorite: Codec[Favorite] =
    (uuid *: int8).imap((user, article) => Favorite(UserId(user), ArticleId(article)))(row =>
      (row.user.value, row.article.value)
    )

  private val All: Query[Void, Favorite] = sql"SELECT favoriter, article FROM favorites".query(favorite)

  private val Insert: Command[Favorite] =
    sql"INSERT INTO favorites (favoriter, article) VALUES ($favorite) ON CONFLICT DO NOTHING".command

  private val Delete: Command[Favorite] =
    sql"DELETE FROM favorites WHERE favoriter = $uuid AND article = $int8".command
      .contramap(row => (row.user.value, row.article.value))

  private val DeleteAllOf: Command[Long] = sql"DELETE FROM favorites WHERE article = $int8".command
