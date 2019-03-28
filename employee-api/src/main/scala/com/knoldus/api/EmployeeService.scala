package com.knoldus.api

import com.example.Employee
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.typesafe.config.ConfigFactory

trait EmployeeService extends Service {

  def employeeInfoTopic: Topic[Employee]

  private val config = ConfigFactory.load()

  val employeeTopicName = config.getString("employee.topic")

  override def descriptor: Descriptor = {

    import Service._

    named("employee-service").withTopics(
      topic(employeeTopicName, this.employeeInfoTopic)
    )
  }
}
