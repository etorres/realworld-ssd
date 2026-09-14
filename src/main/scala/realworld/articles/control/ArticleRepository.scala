package realworld.articles.control

import cats.effect.kernel.{Ref, Sync}
import cats.syntax.all.*

import realworld.articles.entity.{Article, ArticleId, Slug}

/** Storage for articles. Filtering and ordering belong to the service, which knows what the spec asks for. */
trait ArticleRepository[F[_]]:
  def findBySlug(slug: Slug): F[Option[Article]]
  def save(article: Article): F[Unit]
  def delete(id: ArticleId): F[Unit]

  /** Every article, oldest first, so that a stable sort by creation time breaks ties predictably. */
  def all: F[List[Article]]

object ArticleRepository:

  /** In-memory storage, per decision D3. */
  def inMemory[F[_]: Sync]: F[ArticleRepository[F]] =
    Ref
      .of[F, Vector[Article]](Vector.empty)
      .map: store =>
        new ArticleRepository[F]:
          def findBySlug(slug: Slug): F[Option[Article]] = store.get.map(_.find(_.slug == slug))

          def save(article: Article): F[Unit] =
            store.update: stored =>
              stored.indexWhere(_.id == article.id) match
                case -1 => stored :+ article
                case index => stored.updated(index, article)

          def delete(id: ArticleId): F[Unit] = store.update(_.filterNot(_.id == id))

          def all: F[List[Article]] = store.get.map(_.toList)
