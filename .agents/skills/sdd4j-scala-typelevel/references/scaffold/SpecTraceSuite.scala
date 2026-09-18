package «base»

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import munit.FunSuite

/** The gate that keeps the specifications living.
  *
  * A requirement id is traced by appearing literally in a munit test name (decision D7), and
  * `RequirementSuite` builds every name from the id, so the trace can be checked as text — in both
  * directions, with no runtime registration and no ordering between suites.
  *
  * Ids are unique only within a capability: `users` and `tags` both declare an `R1.1`. So a trace counts
  * only inside its own capability's tests, which is what makes "`tags` R1.1 has no test" visible even
  * while some other capability's `R1.1` is thoroughly covered.
  *
  * A capability whose spec exists but whose tests do not yet is reported as pending rather than failed, so
  * that an unapplied capability stays visible without holding the build red.
  */
class SpecTraceSuite extends FunSuite:

  import SpecTraceSuite.*

  test("every requirement of an applied capability is traced by a test") {
    println(report)
    val gaps = applied.flatMap(spec => spec.ids.filterNot(traced(spec).contains).map(spec.name + ": " + _))
    assert(
      gaps.isEmpty,
      s"""|${gaps.size} requirement(s) have no test carrying their id:
          |${gaps.map("  " + _).mkString("\n")}
          |
          |Add a row for each in the capability's RequirementSuite, or retire the statement from the spec.""".stripMargin
    )
  }

  test("every requirement id a test claims exists in its own capability's spec") {
    val orphans = specs.flatMap: spec =>
      (traced(spec) -- spec.ids.toSet).toList.sorted.map(spec.name + ": " + _)
    assert(
      orphans.isEmpty,
      s"""|${orphans.size} test(s) trace an id their capability's spec does not declare:
          |${orphans.map("  " + _).mkString("\n")}
          |
          |This is drift. Either the statement was retired from the spec and the test should go too, or the
          |behaviour is real and belongs in the spec — which is a decision for a human, not for this suite.""".stripMargin
    )
  }

object SpecTraceSuite:

  private val SpecRoot = Path.of("src/main/scala/«base»")
  private val TestRoot = Path.of("src/test/scala/«base»")

  /** `- R1.2 — …` or `- S1 — …` in a spec's Markdown. */
  private val StatementInSpec = """^\s*\*\s*-\s+(R\d+\.\d+|S\d+)\s""".r.unanchored

  /** An id opening a string literal, which is how `RequirementSuite` receives it. */
  private val TraceInTest = """"(R\d+\.\d+|S\d+)\b""".r

  /** One spec, the statements it declares, and the only test sources allowed to trace them. */
  final private case class Spec(name: String, ids: List[String], tests: List[Path])

  private lazy val specs: List[Spec] =
    // System invariants are traced by the suites sitting directly under the test root: they belong to no
    // capability, so they have no directory of their own.
    val system = Spec("system", statementsIn(SpecRoot.resolve("package.scala")), directScalaFiles(TestRoot))
    val components = childDirectories(SpecRoot)
      .map(directory => directory.getFileName.toString -> directory.resolve("package.scala"))
      .filter((_, spec) => Files.isRegularFile(spec))
      .map((name, spec) => Spec(name, statementsIn(spec), scalaFilesUnder(TestRoot.resolve(name))))
    (system :: components).filter(_.ids.nonEmpty)

  private lazy val traces: Map[String, Set[String]] =
    specs.map(spec => spec.name -> spec.tests.flatMap(idsIn).toSet).toMap

  private def traced(spec: Spec): Set[String] = traces.getOrElse(spec.name, Set.empty)

  /** A capability is applied once its own tests claim at least one of its ids. */
  private lazy val (applied, pending) = specs.partition(spec => traced(spec).nonEmpty)

  private lazy val report: String =
    val claimed = traces.values.map(_.size).sum
    val covered = applied.map: spec =>
      f"  ${spec.name}%-12s ${s"${spec.ids.count(traced(spec).contains)}/${spec.ids.size}"}%-9s traced"
    val waiting = pending.map: spec =>
      f"  ${spec.name}%-12s ${s"0/${spec.ids.size}"}%-9s pending — not applied yet"
    (s"spec trace coverage ($claimed ids claimed by tests)" :: covered ::: waiting).mkString("\n")

  private def statementsIn(spec: Path): List[String] =
    if !Files.isRegularFile(spec) then Nil
    else Files.readAllLines(spec).asScala.toList.collect { case StatementInSpec(id) => id }

  private def idsIn(source: Path): List[String] =
    TraceInTest.findAllMatchIn(Files.readString(source)).map(_.group(1)).toList

  private def childDirectories(root: Path): List[Path] =
    if !Files.isDirectory(root) then Nil
    else
      Using.resource(Files.list(root))(
        _.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString)
      )

  private def directScalaFiles(root: Path): List[Path] =
    if !Files.isDirectory(root) then Nil
    else Using.resource(Files.list(root))(_.iterator.asScala.filter(isScala).toList.sortBy(_.toString))

  private def scalaFilesUnder(root: Path): List[Path] =
    if !Files.isDirectory(root) then Nil
    else Using.resource(Files.walk(root))(_.iterator.asScala.filter(isScala).toList.sortBy(_.toString))

  private def isScala(path: Path): Boolean = Files.isRegularFile(path) && path.toString.endsWith(".scala")
