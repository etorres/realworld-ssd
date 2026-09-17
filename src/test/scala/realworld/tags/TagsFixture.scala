package realworld.tags

import cats.effect.IO
import org.http4s.{HttpApp, Method, Request, Response, Uri}

import realworld.support.TestDatabase
import realworld.support.http.Api
import realworld.tags.boundary.{Tags, TagsRoutes}
import realworld.tags.control.TagRegistry

/** Everything the `tags` requirement suites arrange. There is nothing to authenticate and nothing to
  * reject, so this is the whole component and its one route.
  *
  * Backed by PostgreSQL since decision D11. Construction is the only thing that changed: not one row of
  * `RegisterTagsSuite` or `ListTagsSuite` knows where the registry keeps anything.
  */
final case class TagsFixture(tags: Tags[IO], api: HttpApp[IO])

object TagsFixture:

  def apply(): IO[TagsFixture] =
    TestDatabase
      .fresh(TagRegistry.Tables)
      .map: pool =>
        val tags = Tags.postgres[IO](pool)
        TagsFixture(tags, Api(TagsRoutes(tags).routes))

  extension (fixture: TagsFixture)
    /** The registry as plain names, which is what every row asserts against. */
    def inUse: IO[List[String]] = fixture.tags.listTags.map(_.map(_.value))

    def call(method: Method, path: String): IO[Response[IO]] =
      fixture.api.run(Request[IO](method, Uri.unsafeFromString(path)))
