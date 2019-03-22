package com.knoldus

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import com.example.Employee
import com.knoldus.api.EmployeeService
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext

class EmployeeIngestStreamFlow(actorSystem: ActorSystem, registry: PersistentEntityRegistry)(implicit exec: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val globalSettings = actorSystem.settings.config
  private val serviceSettings = globalSettings.getConfig("knoldus.employee.service")
  private val kafkaParallelism = serviceSettings.getInt("kafka.ingest.data-parallelism")

  val sendEmployeeInfoToEntities: Flow[Employee, Done, _] =
    Flow[Employee].mapAsync(kafkaParallelism) { employee =>
      log.debug(s"Got Employee info from Kafka for ${employee.id}")
      registry.refFor[EmployeeEntity](employee.id).ask(HandleIncomingEmployee(employee))
    }

  val employeeInfoToEntitySubscriptionFlow: Flow[Employee, Done, _] = Flow[Employee]
    .via(sendEmployeeInfoToEntities)

}

class EmployeeIngestStream(employeeIngestStreamFlow: EmployeeIngestStreamFlow, employeeService: EmployeeService) {

  lazy val config: Config = ConfigFactory.load()
  val consumerGroup = config.getString("knoldus.employee.service.kafka.consuumerGroup")

  lazy val subscriptionFlow = employeeIngestStreamFlow.employeeInfoToEntitySubscriptionFlow

  employeeService.employeeInfoTopic.subscribe.withGroupId(consumerGroup).atLeastOnce(
    subscriptionFlow
  )
}


