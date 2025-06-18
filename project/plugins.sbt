addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
addSbtPlugin("com.github.sbt" % "flyway-sbt" % "10.21.0")
libraryDependencies += "org.postgresql" % "postgresql" % "42.7.3"
libraryDependencies += "org.flywaydb" % "flyway-database-postgresql" % "10.21.0"