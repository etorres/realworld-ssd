package realworld.support.config

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import cats.syntax.all.*
import ciris.{ConfigValue, Effect, Secret, env}

final case class AppConfig(
    host: String,
    port: Int,
    jwtSecret: Secret[String],
    tokenTtl: FiniteDuration,
    bcryptLogRounds: Int
)

object AppConfig:
  val load: ConfigValue[Effect, AppConfig] =
    (
      env("REALWORLD_HOST").default("0.0.0.0"),
      env("REALWORLD_PORT").as[Int].default(8080),
      env("REALWORLD_JWT_SECRET").secret.default(Secret("dev-secret-do-not-use-in-production")),
      env("REALWORLD_TOKEN_TTL_MINUTES").as[Int].default(60 * 24).map(_.minutes),
      env("REALWORLD_BCRYPT_LOG_ROUNDS").as[Int].default(10)
    ).parMapN(AppConfig.apply)
