ThisBuild / organization := "com.tignear"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / scalacOptions ++= Seq("-Xkind-projector:underscores")

enablePlugins(GraalVMNativeImagePlugin)
enablePlugins(FlywayPlugin)

flywayUrl := "jdbc:postgresql://localhost:55432/pfa_db";
flywayUser := "pfa_user";
flywayPassword := "pfa_pass";
flywayLocations := Seq("filesystem:app/src/main/resources/db/migration");
lazy val root = (project in file("."))
  .settings(
    name := "root",
    Compile / mainClass := Some("com.tignear.pfa.HelloWorld"),
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "--install-exit-handlers",
      "--enable-http",
      "--enable-url-protocols=http,https"
    )
  )
  .dependsOn(app)

lazy val app = (project in file("app"))
  .settings(
    name := "app",
    libraryDependencies ++= Seq(
      dependencies.http4s_ember,
      dependencies.zio_interop_cats,
      dependencies.tapir_http4s_server_zio,
      dependencies.tapir_json_circe,
      dependencies.zio,
      dependencies.zio_test,
      dependencies.zio_test_sbt,
      dependencies.quill_jdbc_zio,
      dependencies.postgresql
    )
  )
val http4sVersion = "0.23.30"
val zioVersion = "2.1.19"
lazy val dependencies =
  new {
    val http4s_ember = "org.http4s" %% "http4s-ember-server" % http4sVersion;
    val zio_interop_cats = "dev.zio" %% "zio-interop-cats" % "23.1.0.5";
    val tapir_http4s_server_zio =
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio" % "1.11.34"
    val tapir_json_circe =
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.34";
    val zio = "dev.zio" %% "zio" % zioVersion;
    val zio_test = "dev.zio" %% "zio-test" % zioVersion % Test;
    val zio_test_sbt = "dev.zio" %% "zio-test-sbt" % zioVersion % Test;
    val quill_jdbc_zio = "io.getquill" %% "quill-jdbc-zio" % "4.7.3";
    val postgresql = "org.postgresql" % "postgresql" % "42.7.3";
  }

// テスト用DBのFlyway設定（Testスコープ）
Test / flywayUrl := "jdbc:postgresql://localhost:55532/pfa_test_db"
Test / flywayUser := "pfa_user"
Test / flywayPassword := "pfa_pass"
Test / flywayLocations := Seq("filesystem:app/src/main/resources/db/migration")
