package com.knoldus.impl

import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.example.Employee
import com.knoldus.api.EmployeeService
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

class EmployeeIngestStreamFlow(actorSystem: ActorSystem)(implicit exec: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val globalSettings = actorSystem.settings.config
  private val serviceSettings = globalSettings.getConfig("knoldus.employee.service")
  private val kafkaParallelism = serviceSettings.getInt("kafka.ingest.data-parallelism")

  val processEmployeeInfoFlow: Flow[Employee, Employee, _] = Flow[Employee]
    .mapAsync(kafkaParallelism) { employee =>
      log.debug(s"Got Employee info from Kafka for [${employee.id}] now processing the info")
      println(s"here is the employee ${employee.toString}")
      //TODO: Paste your logic here
      Future.successful(employee)
    }

  val terminateFlow: Flow[Any, Done, NotUsed] = Flow[Any].map { _ => Done }

  val employeeInfoSubscriptionFlow: Flow[Employee, Done, _] = Flow[Employee]
    .via(processEmployeeInfoFlow)
    .via(terminateFlow)

}

class EmployeeIngestStream(employeeIngestStreamFlow: EmployeeIngestStreamFlow, employeeService: EmployeeService) {

  lazy val config: Config = ConfigFactory.load()
  val consumerGroup: String = config.getString("knoldus.employee.service.kafka.consumerGroup")

  lazy val subscriptionFlow: Flow[Employee, Done, _] = employeeIngestStreamFlow.employeeInfoSubscriptionFlow

  employeeService.employeeInfoTopic.subscribe.withGroupId(consumerGroup).atLeastOnce(
    subscriptionFlow
  )
}


