package com.knoldus

import akka.Done
import akka.actor.ActorSystem
import com.example.Employee
import com.google.common.reflect.ClassPath.ClassInfo
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, Json}

class EmployeeEntity(actorSystem: ActorSystem) extends PersistentEntity {

  override type Command = EmployeeCommand
  override type Event = EmployeeEvent
  override type State = EmployeeEntityState

  private val log: Logger = LoggerFactory.getLogger(getClass)

  override def initialState: State = EmployeeEntityState.initial(entityId, salary = 0)

  override def behavior: Behavior = {
    initialized
  }

  private def initialized: Actions = {
    Actions()
      .onCommand[HandleIncomingEmployee, Done] {
      case (HandleIncomingEmployee(employee: Employee), ctx, state) =>
        val events = if (state.employeeId.isEmpty) {
          log.info(s"Adding Employee with EmployeeID: ${employee.id} to EmployeeEntity for the first time.")
          List(EmployeeAdded(employee))
        } else {
          log.info(s"Updating Employee with EmployeeID: ${employee.id} in EmployeeEntity.")
          List(EmployeeUpdated(employee))
        }
        persistedEventsAndReply(events)(ctx)
    }
      .onEvent {
        case (EmployeeAdded(_), state) => state.copy(salary = 1000)
        case (EmployeeUpdated(_), state) => state.copy(salary = 2000)

      }

  }

  private def persistedEventsAndReply(eventsToPersist: List[EmployeeEvent])(implicit ctx: EmployeeEntity.this.CommandContext[Done]): Persist = {
    if (eventsToPersist.nonEmpty) {
      ctx.thenPersistAll(eventsToPersist: _*)(() => ctx.reply(Done))
    } else {
      ctx.reply(Done)
      ctx.done
    }
  }

}

/**
  * Commands Issued to an entity
  */
sealed trait EmployeeCommand

case class AddEmployee(employee: Employee) extends EmployeeCommand with ReplyType[Done]
object AddEmployee {
  implicit val format: Format[AddEmployee] = Json.format
}

case class UpdateEmployee(employee: Employee) extends EmployeeCommand with ReplyType[Done]
object UpdateEmployee {
  implicit val format: Format[UpdateEmployee] = Json.format
}

case class HandleIncomingEmployee(Employee: Employee) extends EmployeeCommand with ReplyType[Done]

object HandleIncomingEmployee {
  implicit val format: Format[HandleIncomingEmployee] = Json.format
}

object EmployeeCommand {
  implicit val format: Format[EmployeeCommand] = Json.format
}

sealed trait EmployeeEvent extends AggregateEvent[EmployeeEvent] {
  override def aggregateTag: AggregateEventTagger[EmployeeEvent] = EmployeeEvent.tag
  implicit val format: Format[EmployeeEvent] = Json.format
}

case class EmployeeAdded(Employee: Employee) extends EmployeeEvent

object EmployeeAdded {
  implicit val format: Format[EmployeeAdded] = Json.format
}

case class EmployeeUpdated(Employee: Employee) extends EmployeeEvent

object EmployeeUpdated {
  implicit val format: Format[EmployeeUpdated] = Json.format
}

object EmployeeEvent {

  implicit val format = Json.format[EmployeeEvent]
  private val NumShards = 20
  private val tag: AggregateEventShards[EmployeeEvent] = AggregateEventTag.sharded[EmployeeEvent](NumShards)
}

/**
  * Entity State
  */
case class EmployeeEntityState(employeeId: String, salary: Long)

object EmployeeEntityState {
  implicit val format: Format[EmployeeEntityState] = Json.format
  def initial(employeeId: String, salary: Long): EmployeeEntityState = new EmployeeEntityState(employeeId, salary)
}

