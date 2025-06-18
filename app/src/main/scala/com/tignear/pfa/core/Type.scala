package com.tignear.pfa.core

import zio.json.JsonEncoder
import zio.json.DeriveJsonEncoder

object Types {
  type UserId = String
}
trait Command {
  def userId: Types.UserId
}
trait Event {
  def userId: Types.UserId
  def eventType: String
}
sealed trait InfraStructureError
object InfraStructureError {
  case class DatabaseError(cause: Throwable) extends InfraStructureError
}
