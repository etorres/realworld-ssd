package realworld

import cats.effect.IO
import munit.CatsEffectSuite

/** Base for the tests of one `### Rn` requirement group.
  *
  * The shape follows `sdd4j-ears-tests`: one suite per group, one row per EARS statement. The runner-visible
  * test name is built from the statement id here and nowhere else, so the trace `SpecTraceSuite` checks
  * cannot drift from the name the runner actually prints.
  *
  * `Setting` is whatever the group's rows need arranged before they act.
  */
abstract class RequirementSuite[Setting] extends CatsEffectSuite:

  /** Arranged afresh for every row, so no row can observe another's writes. */
  protected def setting: IO[Setting]

  /** One row per statement: its id, what it asserts, and the check. */
  protected def requirements(rows: (String, String, Setting => IO[Unit])*)(using munit.Location): Unit =
    rows.foreach: (id, statement, check) =>
      test(s"$id — $statement")(setting.flatMap(check))
