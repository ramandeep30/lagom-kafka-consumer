package com.knoldus.impl

import com.example.Employee
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}

import scala.collection.immutable.Seq

object SystemSerializerRegistry extends JsonSerializerRegistry {

  override def serializers: Seq[JsonSerializer[_]] = Seq(
   JsonSerializer[Employee]
  )
}
