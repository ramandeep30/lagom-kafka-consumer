package com.example

import play.api.libs.json.Json

case class Employee(id: String, employeeType: String, name: String, designation: String, salary: Long)

object Employee {

  implicit val _format = Json.format[Employee]
}
