package com.tignear.pfa.feature.transaction



import com.tignear.pfa.core.Types.UserId
import com.tignear.pfa.core.InfraStructureError
import java.time.Instant
import zio._

trait TransactionEventReader {
  /**
   * Reads events for a user in a given time range.
   * If from is None, returns all events before 'to'.
   * If to is None, returns all events after 'from'.
   * If both are None, returns all events for the user.
   */
  def read(
    userId: UserId,
    from: Option[Instant],
    to: Option[Instant]
  ): ZIO[Any, InfraStructureError, List[TransactionEvent]]
}
