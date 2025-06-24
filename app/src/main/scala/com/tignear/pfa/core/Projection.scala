package com.tignear.pfa.core
import zio._

trait ProjectionLogic[State, Evt] {
  def accumulate(currentState: Option[State], event: Evt): State
}
trait StateRepository[Key, State] {
  def load(key: Key): IO[InfraStructureError, Option[State]]
  def save(state: State): IO[InfraStructureError, Unit]
}
trait EventKeyExtractor[Evt, Key] {
  def getKey(event: Evt): Key
}

trait ProjectionDef[State, Evt, Key] {
  def projectionName: String
  def logic: ProjectionLogic[State, Evt]
  def keyExtractor: EventKeyExtractor[Evt, Key]
}