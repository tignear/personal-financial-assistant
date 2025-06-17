ThisBuild / organization := "com.tignear"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / scalacOptions ++= Seq("-Xkind-projector:underscores")

enablePlugins(GraalVMNativeImagePlugin)
lazy val root = (project in file("."))
  .settings(
    Compile / mainClass := Some("com.tignear.HelloWorld"),
    containerBuildImage := Some("ghcr.io/graalvm/native-image-community:24.0.1"),
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "--install-exit-handlers",
      "--enable-http",
      "--enable-url-protocols=http,https"
    )
  )
  .aggregate(app)
  .dependsOn(app)

lazy val app = (project in file("app"))
  .settings(
    libraryDependencies ++= Seq(
      dependencies.http4s_ember,
      dependencies.zio_interop_cats,
      dependencies.tapir_http4s_server_zio,
      dependencies.tapir_json_circe
    )
  )
val http4sVersion = "0.23.30"
lazy val dependencies =
  new {
    val http4s_ember = "org.http4s" %% "http4s-ember-server" % "0.23.30";
    val zio_interop_cats = "dev.zio" %% "zio-interop-cats" % "23.1.0.5";
    val tapir_http4s_server_zio =
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio" % "1.11.34"
    val tapir_json_circe =
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.34"
  }
