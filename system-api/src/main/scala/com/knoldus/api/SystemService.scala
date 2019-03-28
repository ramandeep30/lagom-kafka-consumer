package com.knoldus.api

import com.lightbend.lagom.scaladsl.api.{Descriptor, Service}

trait SystemService extends Service {

  final override def descriptor: Descriptor = {
    import Service._
    named("lagom-kafka-consumer")
  }
}
