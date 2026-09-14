package realworld.tags

import cats.effect.IO
import org.http4s.{HttpApp, Method, Request, Response, Uri}

import realworld.support.http.Api
import realworld.tags.boundary.{Tags, TagsRoutes}

/** Everything the `tags` requirement suites arrange. There is nothing to authenticate and nothing to
  * reject, so this is the whole component and its one route.
  */
final case class TagsFixture(tags: Tags[IO], api: HttpApp[IO])

object TagsFixture:

  def apply(): IO[TagsFixture] =
    Tags.inMemory[IO].map(tags => TagsFixture(tags, Api(TagsRoutes(tags).routes)))

  extension (fixture: TagsFixture)
    /** The registry as plain names, which is what every row asserts against. */
    def inUse: IO[List[String]] = fixture.tags.listTags.map(_.map(_.value))

    def call(method: Method, path: String): IO[Response[IO]] =
      fixture.api.run(Request[IO](method, Uri.unsafeFromString(path)))
