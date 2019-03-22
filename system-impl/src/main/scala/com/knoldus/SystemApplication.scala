package com.knoldus

import akka.stream.Materializer
import com.knoldus.api.EmployeeService
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocatorComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import org.slf4j.LoggerFactory
import play.api.{Environment, LoggerConfigurator}
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents

import scala.concurrent.ExecutionContext

trait SystemApplicationComponents extends LagomServerComponents with CassandraPersistenceComponents {

  implicit def materializer: Materializer

  implicit def executionContext: ExecutionContext

  def environment: Environment

  override lazy val lagomServer: LagomServer = serverFor[SystemService](wire[SystemServiceImpl])

  lazy val employeeIngestStreamFlow: EmployeeIngestStreamFlow = wire[EmployeeIngestStreamFlow]
  persistentEntityRegistry.register(wire[EmployeeEntity])

}

abstract class SystemApplication(context: LagomApplicationContext) extends LagomApplication(context)
  with SystemApplicationComponents
  with AhcWSComponents
  with LagomKafkaComponents {

  lazy val employeeService: EmployeeService = serviceClient.implement[EmployeeService]
  override lazy val jsonSerializerRegistry: SystemSerializerRegistry.type = SystemSerializerRegistry
  protected val log = LoggerFactory.getLogger(getClass)
  log.info("This cluster member is up")
  log.info("Wiring the EmployeeIngestStream")
  wire[EmployeeIngestStream]

}

class SystemApplicationLoader extends LagomApplicationLoader {

  private def initLogging(context: LagomApplicationContext): Unit = {
    val environment = context.playContext.environment
    LoggerConfigurator(environment.classLoader).foreach {
      _.configure(environment)
    }
  }
  override def load(context: LagomApplicationContext): LagomApplication = new SystemApplication(context) with ConfigurationServiceLocatorComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication = {
    initLogging(context)
    new SystemApplication(context) with LagomDevModeComponents
  }

  override def describeService: Option[Descriptor] = Some(readDescriptor[SystemService])
}
