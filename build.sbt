import sbt.Keys._
import sbt._
import Dependencies._

organization in ThisBuild := "knoldus"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.12.4"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test

lazy val `common` = (project in file("common"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      kafkaClients
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
  .settings(commonLagomAPISettings: _*)
  .dependsOn(`common`, `employee-api`)

lazy val `system-impl` = (project in file("system-impl"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer,
      lagomScaladslDevMode,
      lagomScaladslCluster,
      lagomScaladslPersistence,
      lagomScaladslPersistenceCassandra,
      macwire
    )
  )
  .settings(commonLagomImplSettings: _*)
  .dependsOn(`common`, `employee-api`, `system-api`)

lazy val `lagom-kafka-consumer-demo` = (project in file("."))
  .aggregate(
    `common`,
    `employee-api`,
    `system-api`,
    `system-impl`
  )

val commonLagomAPISettings = Seq(
  libraryDependencies ++= Seq(
    lagomScaladslApi
  )
)

val commonLagomImplSettings = Seq(
  libraryDependencies ++= Seq(
    lagomScaladslPersistenceCassandra,
    lagomScaladslTestKit,
    lagomScaladslPersistence,
    lagomScaladslKafkaBroker
  )
)



