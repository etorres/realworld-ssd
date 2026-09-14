package realworld.tags

import cats.effect.IO

import realworld.RequirementSuite

import TagsFixture.*

/** R1: Register tags in use. */
class RegisterTagsSuite extends RequirementSuite[TagsFixture]:

  protected def setting: IO[TagsFixture] = TagsFixture()

  requirements(
    (
      "R1.1",
      "adds each registered tag to the set in use",
      fixture =>
        for
          _ <- fixture.tags.registerTags(List("dragons", "training"))
          inUse <- fixture.inUse
        yield assertEquals(inUse.toSet, Set("dragons", "training"))
    ),
    (
      "R1.2",
      "leaves the set unchanged when a tag already in use is registered again",
      fixture =>
        for
          _ <- fixture.tags.registerTags(List("dragons", "training"))
          before <- fixture.inUse
          // Once again in a later call, and twice within a single one.
          _ <- fixture.tags.registerTags(List("dragons"))
          _ <- fixture.tags.registerTags(List("training", "training"))
          after <- fixture.inUse
        yield
          assertEquals(after, before, "re-registering a tag already in use changed the set")
          assertEquals(after.count(_ == "dragons"), 1)
    ),
    (
      "R1.3",
      "ignores a blank tag instead of rejecting the registration",
      fixture =>
        for
          _ <- fixture.tags.registerTags(List("dragons", "", "   ", "training"))
          inUse <- fixture.inUse
        yield assertEquals(inUse.toSet, Set("dragons", "training"), "a blank tag reached the set in use")
    )
  )
