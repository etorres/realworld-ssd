package realworld.support

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import skunk.implicits.*
import skunk.{Command, Session, Void}

import realworld.support.storage.Database

/** One PostgreSQL container for the whole test run, and an empty schema for each fixture that asks.
  *
  * A `Ref`-backed fixture started empty because it allocated its own `Ref`. A database-backed one has to
  * be *made* empty, and the cheapest honest way is to drop the schema and rebuild it — which is why
  * `Test / parallelExecution` is off in `build.sbt`: two suites resetting the same schema at once would
  * see each other's tables vanish.
  *
  * That cost is the point rather than an accident. Decision D3 justified in-memory storage by how fast
  * the spec-test-code loop stays, and this is where that claim gets its bill.
  */
object TestDatabase:

  /** Started once, on first use, and left to the JVM's exit to stop -- Testcontainers' own reaper removes
    * it even if the JVM is killed.
    */
  private lazy val container: PostgreSQLContainer =
    // Testcontainers asks for Docker API 1.32 unless told otherwise, and engines from Docker 25 onward
    // refuse anything below 1.40. Set before the first client is built, which is the line below.
    val _ = System.setProperty("api.version", "1.43")
    val started = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
    started.start()
    started

  /** Allocated once and never released, for the same reason the container is: the pool outlives every
    * individual fixture, and the run's end is the only sensible point to close it.
    */
  private lazy val pool: Resource[IO, Session[IO]] =
    Database
      .pool[IO](
        Database.Settings(
          host = container.container.getHost,
          port = container.container.getFirstMappedPort.toInt,
          user = container.username,
          password = container.password.some,
          database = container.databaseName,
          poolSize = 4
        )
      )
      .allocated
      .unsafeRunSync()
      ._1

  /** A pool over an empty schema holding exactly the tables asked for. */
  def fresh(tables: List[Command[Void]]): IO[Resource[IO, Session[IO]]] =
    pool.use(session => Reset.traverse_(session.execute)) *>
      Database.migrate(pool, tables).as(pool)

  private val Reset: List[Command[Void]] = List(
    sql"DROP SCHEMA public CASCADE".command,
    sql"CREATE SCHEMA public".command
  )
