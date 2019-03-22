package com.example

object CommonTopics {

  val EmployeeTopic = KafkaTopicString(
    name = "employee-info-json",
    description = "Information about Employees",
    keyDescription = "id",
    valueDescription = "Employee as Json",
    compact = true
  )
}
