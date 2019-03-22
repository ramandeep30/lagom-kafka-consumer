import sbt._

object Dependencies extends AutoPlugin {

  val kafkaVersion = "0.10.2.0"
  val akkaHttpVersion = "10.1.6"
  val kafkaClients = "org.apache.kafka" % "kafka-clients" % kafkaVersion
  val kafka = "org.apache.kafka" %% "kafka" % kafkaVersion
  val zookeeper = "org.apache.zookeeper" % "zookeeper" % "3.4.11"
}
