package com.knoldus.impl

import com.knoldus.api.{EmployeeService, SystemService}
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaClientComponents
import com.lightbend.lagom.scaladsl.client.ConfigurationServiceLocatorComponents
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServer}
import com.softwaremill.macwire.wire
import play.api.libs.ws.ahc.AhcWSComponents

/**
  * The class SystemApplicationLoader uses LagomApplicationLoader to load the application and does the
  * the binding of modules at runtime.
  */
class SystemApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new SystemApplication(context) with ConfigurationServiceLocatorComponents {}

  override def loadDevMode(context: LagomApplicationContext): LagomApplication = {
    new SystemApplication(context) with LagomDevModeComponents
  }

  override def describeService: Option[Descriptor] = Some(readDescriptor[SystemService])
}

abstract class SystemApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with ClusterComponents
    with AhcWSComponents
    with LagomKafkaClientComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[SystemService](wire[SystemServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = SystemSerializerRegistry

  // Initialise everything
  wire[EmployeeIngestStream]

  lazy val employeeService: EmployeeService = serviceClient.implement[EmployeeService]
  lazy val employeeIngestStreamFlow: EmployeeIngestStreamFlow = wire[EmployeeIngestStreamFlow]
}
