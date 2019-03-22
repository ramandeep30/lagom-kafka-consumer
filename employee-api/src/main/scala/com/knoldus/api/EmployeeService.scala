package com.knoldus.api

import com.example.Employee
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.typesafe.config.ConfigFactory
import com.example.CommonTopics._

trait EmployeeService extends Service {

  def employeeInfoTopic: Topic[Employee]

  private val config = ConfigFactory.load()

  private val environment = config.getString("employee.environment")

  override def descriptor: Descriptor = {

    import Service._

    named("employee-service").withTopics(
      topic(EmployeeTopic.fromEnvironment(environment).name, this.employeeInfoTopic)
    )
  }
}
