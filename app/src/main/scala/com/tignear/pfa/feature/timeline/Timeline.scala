package com.tignear.pfa.feature.timeline

import com.tignear.pfa.core.InfraStructureError
import java.time.Instant
import com.tignear.pfa.core.Types.UserId
import zio.ZIO
import zio.Cause
import com.tignear.pfa.feature.transaction.TransactionEventReader
import com.tignear.pfa.feature.transaction.TransactionEvent

sealed trait TimelineQueryError
object TimelineQueryError {
  case object InvalidRange extends TimelineQueryError
}

case class TimelineSummary(
    userId: UserId,
    from: Instant,
    to: Instant,
    beforeAmount: Long,
    totalExpense: Long
) {
  def afterAmount = beforeAmount - totalExpense
}

class Timeline(reader: TransactionEventReader) {
  def getTimeline(
      userId: UserId,
      from: Instant,
      to: Instant
  ): ZIO[
    Any,
    TimelineQueryError | InfraStructureError,
    (TimelineSummary, List[TransactionEvent])
  ] = {
    if (from.isAfter(to))
      ZIO.fail(TimelineQueryError.InvalidRange)
    else
      for {
        histories <- reader.read(userId, None, Some(to))
      } yield {
        val totalExpense = histories
          .filter(!_.transactionDate.isBefore(from))
          .map(_.amount)
          .sum
        val beforeAmount = histories
          .filter(_.transactionDate.isBefore(from))
          .map(_.amount)
          .sum
        (
          TimelineSummary(
            userId = userId,
            from = from,
            to = to,
            beforeAmount = beforeAmount,
            totalExpense = totalExpense
          ),
          histories.filter(!_.transactionDate.isBefore(from))
        )
      }
  }
}
