package com.tignear.pfa.core

object Types {
  case class UserId(id: Long);
}
trait Command{
  def userId: Types.UserId
}
trait Event{
  def userId: Types.UserId
  def eventType: String
}
sealed trait InfraStructureError
case class DatabaseError(cause: Throwable) extends InfraStructureError