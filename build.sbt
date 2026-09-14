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
val munitCeV    = "2.2.0"

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
      "org.typelevel"        %% "munit-cats-effect"          % munitCeV % Test,
      "org.typelevel"        %% "cats-effect-testkit"        % catsEffectV % Test
    )
  )
