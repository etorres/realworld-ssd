package realworld.articles.control

import cats.effect.kernel.Sync
import cats.syntax.all.*
import io.hypersistence.tsid.TSID

import realworld.articles.entity.ArticleId

/** Hands out article identifiers.
  *
  * An algebra rather than a function because the generator is stateful: within one millisecond it
  * increments a counter instead of re-reading the clock, and that is exactly what makes consecutive
  * identifiers ordered. One instance per node, held for the life of the component.
  */
trait ArticleIds[F[_]]:
  def next: F[ArticleId]

object ArticleIds:

  /** The node identifier distinguishes generators that run at the same time on different servers. Two
    * identifiers minted in the same millisecond on different nodes are ordered by node rather than by
    * which came first — R2.1 is silent on ties, so that is a stable answer rather than a true one.
    */
  def tsid[F[_]: Sync](node: Int): F[ArticleIds[F]] =
    Sync[F]
      .delay(TSID.Factory.builder().withNode(node).build())
      .map: factory =>
        new ArticleIds[F]:
          // The factory reads a clock and mutates a counter, so every call is suspended.
          def next: F[ArticleId] = Sync[F].delay(ArticleId(factory.generate().toLong))
