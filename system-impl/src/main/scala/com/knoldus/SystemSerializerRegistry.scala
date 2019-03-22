package com.knoldus

import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import scala.collection.immutable.Seq

object SystemSerializerRegistry extends JsonSerializerRegistry {

  override def serializers: Seq[JsonSerializer[_]] = Seq(
   JsonSerializer[EmployeeEntityState],
    JsonSerializer[EmployeeCommand],
    JsonSerializer[AddEmployee],
    JsonSerializer[UpdateEmployee],
    JsonSerializer[HandleIncomingEmployee],
    JsonSerializer[EmployeeEvent],
    JsonSerializer[EmployeeAdded],
    JsonSerializer[EmployeeUpdated]
  )
}
