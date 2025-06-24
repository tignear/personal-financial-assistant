package com.tignear.pfa.infrastructure

import zio._
import zio.stream._
import com.tignear.pfa.core.ProjectionLogic
import com.tignear.pfa.core.StateRepository
import com.tignear.pfa.core.EventKeyExtractor
import com.tignear.pfa.core.InfraStructureError
import com.tignear.pfa.core.ProjectionDef

object ProjectionRunner {
  def run[State: Tag, Payload: Tag, Key: Tag](
      events: List[ReadEvent[Payload]]
  ): ZIO[
    CheckpointRepository & Transactional & StateRepository[Key, State] &
      ProjectionDef[State, ReadEvent[Payload], Key],
    InfraStructureError,
    Unit
  ] = {
    val setup = for {
      cpRepo <- ZIO.service[CheckpointRepository]
      stateRepo <- ZIO.service[StateRepository[Key, State]]
      projectionDef <- ZIO
        .service[ProjectionDef[State, ReadEvent[Payload], Key]]
      transactional <- ZIO.service[Transactional]
      lastEventId <- cpRepo
        .load(projectionDef.projectionName)
        .map(_.map(_.lastEventId).getOrElse(0L))
    } yield (
      cpRepo,
      stateRepo,
      projectionDef,
      transactional,
      lastEventId
    )

    setup.flatMap {
      case (cpRepo, stateRepo, projectionDef, transactional, lastEventId) =>
        val coreLogic = {
          val eventStream = ZStream.fromIterable(
            events.filter(_.id > lastEventId).sortBy(_.id)
          )

          val processingStream = eventStream.mapZIO { event =>
            for {
              key <- ZIO.succeed(projectionDef.keyExtractor.getKey(event))
              maybeCurrentState <- stateRepo.load(key)
              nextState = projectionDef.logic.accumulate(
                maybeCurrentState,
                event
              )
              _ <- stateRepo.save(nextState)
            } yield event
          }

          for {
            maybeLastEvent <- processingStream.run(ZSink.last)
            _ <- ZIO.foreach(maybeLastEvent) { lastEvent =>
              cpRepo.save(
                Checkpoint(projectionDef.projectionName, lastEvent.id)
              )
            }
          } yield ()
        }
        coreLogic @@ transactional.aspect
    }
  }
}
