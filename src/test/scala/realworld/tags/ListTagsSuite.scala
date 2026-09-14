package realworld.tags

import cats.effect.IO
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite

import TagsFixture.*

/** R2: List tags in use. */
class ListTagsSuite extends RequirementSuite[TagsFixture]:

  protected def setting: IO[TagsFixture] = TagsFixture()

  requirements(
    (
      "R2.1",
      "returns every registered tag exactly once",
      fixture =>
        for
          // Overlapping batches, so a tag in use twice must still be reported once.
          _ <- fixture.tags.registerTags(List("dragons", "training"))
          _ <- fixture.tags.registerTags(List("training", "reactjs"))
          response <- fixture.call(Method.GET, "/api/tags")
          body <- response.as[Json]
          reported = body.hcursor.downField("tags").as[List[String]]
        yield
          assertEquals(response.status, Status.Ok)
          assertEquals(reported.map(_.toSet), Right(Set("dragons", "training", "reactjs")))
          assertEquals(reported.map(_.size), Right(3), s"a tag was reported more than once: $body")
    ),
    (
      "R2.2",
      "returns an empty list while no tag has been registered",
      fixture =>
        for
          response <- fixture.call(Method.GET, "/api/tags")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Ok)
          assertEquals(body.hcursor.downField("tags").as[List[String]], Right(Nil))
    )
  )
