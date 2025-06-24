package com.tignear.pfa.infrastructure

import com.tignear.pfa.core.InfraStructureError
import zio._
final case class Checkpoint(
    projectorName: String,
    lastEventId: Long
)
trait CheckpointRepository {
  def load(projectorName: String): IO[InfraStructureError, Option[Checkpoint]]
  def save(checkpoint: Checkpoint): IO[InfraStructureError, Unit]
}

object CheckpointRepository {
  def load(
      projectorName: String
  ): ZIO[CheckpointRepository, InfraStructureError, Option[Checkpoint]] =
    ZIO.serviceWithZIO[CheckpointRepository](_.load(projectorName))

  def save(
      checkpoint: Checkpoint
  ): ZIO[CheckpointRepository, InfraStructureError, Unit] =
    ZIO.serviceWithZIO[CheckpointRepository](_.save(checkpoint))
}