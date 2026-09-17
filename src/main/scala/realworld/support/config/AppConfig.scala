package realworld.support.config

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import cats.syntax.all.*
import ciris.{ConfigValue, Effect, Secret, env}

import realworld.support.storage.Database

final case class AppConfig(
    host: String,
    port: Int,
    jwtSecret: Secret[String],
    tokenTtl: FiniteDuration,
    bcryptLogRounds: Int,
    database: Database.Settings
)

object AppConfig:

  /** Storage settings, since decision D11. The defaults match `docker-compose.yml`. */
  private val database: ConfigValue[Effect, Database.Settings] =
    (
      env("REALWORLD_DB_HOST").default("localhost"),
      env("REALWORLD_DB_PORT").as[Int].default(5432),
      env("REALWORLD_DB_USER").default("realworld"),
      env("REALWORLD_DB_PASSWORD").default("realworld").map(Option(_)),
      env("REALWORLD_DB_NAME").default("realworld"),
      env("REALWORLD_DB_POOL_SIZE").as[Int].default(8)
    ).parMapN(Database.Settings.apply)

  val load: ConfigValue[Effect, AppConfig] =
    (
      env("REALWORLD_HOST").default("0.0.0.0"),
      env("REALWORLD_PORT").as[Int].default(8080),
      env("REALWORLD_JWT_SECRET").secret.default(Secret("dev-secret-do-not-use-in-production")),
      env("REALWORLD_TOKEN_TTL_MINUTES").as[Int].default(60 * 24).map(_.minutes),
      env("REALWORLD_BCRYPT_LOG_ROUNDS").as[Int].default(10),
      database
    ).parMapN(AppConfig.apply)
