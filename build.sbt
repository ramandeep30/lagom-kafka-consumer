
organization in ThisBuild := "knoldus"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.12.4"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test

lazy val `lagom-kafka-consumer` = (project in file("."))
  .aggregate(`common`, `employee-api`, `system-api`, `system-impl`)

lazy val `common` = (project in file("common"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `employee-api` = (project in file("employee-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )
  .dependsOn(`common`)

lazy val `system-api` = (project in file("system-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )
  .dependsOn(`common`, `employee-api`)

lazy val `system-impl` = (project in file("system-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`system-api`)

lagomCassandraEnabled in ThisBuild := false //set it to false to use external Cassandra
lagomKafkaEnabled in ThisBuild := false //set it to false to use external Kafka