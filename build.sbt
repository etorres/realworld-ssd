ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "realworld"
ThisBuild / version      := "0.1.0-SNAPSHOT"

val catsEffectV = "3.7.1"
val catsMtlV    = "1.7.0"
val http4sV     = "0.23.37"
val circeV      = "0.14.16"
val kindlingsV  = "0.3.2"
val jwtV        = "11.0.4"
val cirisV      = "3.15.1"
val log4catsV   = "2.8.0"
val logbackV    = "1.5.18"
val jbcryptV    = "0.4"
val tsidV       = "2.1.4"
val munitCeV    = "2.2.0"
val skunkV      = "0.6.4"
val tcV         = "0.43.0"
val tcJavaV     = "1.21.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "realworld-backend",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Xmax-inlines:64",
      // Capability spec files (`<component>/package.scala`) hold nothing but the spec scaladoc and the
      // package clause -- see AGENTS.md, decision D1. That is deliberate, not an omission.
      "-Wconf:msg=is defined in the compilation unit:s"
    ),
    // A database-backed fixture resets a shared schema, so two suites doing it at once would see each
    // other's tables disappear. The cost of that serialisation is a finding of the storage swap, not a
    // workaround for it -- see docs/postgres-swap.md, H8.
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(
      "org.typelevel"        %% "cats-effect"                % catsEffectV,
      "org.typelevel"        %% "cats-mtl"                   % catsMtlV,
      "org.http4s"           %% "http4s-ember-server"        % http4sV,
      "org.http4s"           %% "http4s-dsl"                 % http4sV,
      "org.http4s"           %% "http4s-circe"               % http4sV,
      "io.circe"             %% "circe-core"                 % circeV,
      "io.circe"             %% "circe-parser"               % circeV,
      "com.kubuszok"         %% "kindlings-circe-derivation" % kindlingsV,
      "com.github.jwt-scala" %% "jwt-circe"                  % jwtV,
      "is.cir"               %% "ciris"                      % cirisV,
      "org.typelevel"        %% "log4cats-slf4j"             % log4catsV,
      "ch.qos.logback"        % "logback-classic"            % logbackV % Runtime,
      "org.mindrot"           % "jbcrypt"                    % jbcryptV,
      "io.hypersistence"      % "hypersistence-tsid"         % tsidV,
      "org.tpolecat"         %% "skunk-core"                 % skunkV,
      "org.typelevel"        %% "munit-cats-effect"          % munitCeV % Test,
      "org.typelevel"        %% "cats-effect-testkit"        % catsEffectV % Test,
      "com.dimafeng"         %% "testcontainers-scala-postgresql" % tcV % Test,
      // testcontainers-scala 0.43.0 pins testcontainers-java 1.20.2, which negotiates Docker API
      // 1.32 and is refused by engines requiring 1.40 or newer.
      "org.testcontainers"    % "testcontainers"                  % tcJavaV % Test,
      "org.testcontainers"    % "postgresql"                      % tcJavaV % Test
    )
  )
