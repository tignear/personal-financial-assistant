package com.tignear.pfa.feature.transaction.postgres

import com.tignear.pfa.core.Types.UserId
import com.tignear.pfa.core.InfraStructureError
import io.getquill.jdbczio.Quill
import io.getquill._
import zio._
import java.time.Instant
import io.circe.parser.decode
import io.circe.Json
import io.circe.generic.auto._
import com.tignear.pfa.feature.transaction.TransactionEventReader
import com.tignear.pfa.feature.transaction.TransactionEvent
import com.tignear.pfa.feature.transaction.TransactionExpenseEvent
import com.tignear.pfa.infrastructure.Event
class PostgresTransactionEventReader(ctx: Quill.Postgres[SnakeCase])
    extends TransactionEventReader {
  import ctx._
  import io.getquill._
  override def read(
      userId: UserId,
      from: Option[Instant],
      to: Option[Instant]
  ): ZIO[Any, InfraStructureError, List[TransactionEvent]] = {
    // Note: Filtering by transactionDate inside JSON is not efficient; ideally, store transactionDate as a column
    ctx
      .run(
        query[Event[PostgresTransactionEventPayload]]
          .filter(_.stream_type == "transaction")
          .filter(_.stream_id == lift(userId))
          .filter(_.event_type == "TransactionExpenseEvent")
      )
      .mapBoth(
        e => InfraStructureError.DatabaseError(e),
        rows =>
          rows.flatMap { row =>
            val eventType = row.event_type
            val payload = row.payload.value
            eventType match {
              case "TransactionExpenseEvent" =>
                Some(
                  TransactionExpenseEvent(
                    userId = row.user_id.get,
                    amount = payload.amount,
                    transactionDate = payload.transactionDate
                  )
                )
              // Add more cases for other event types if needed
              case _ => None
            }
          }
      )
  }
}

object PostgresTransactionEventReader {
  val layer
      : ZLayer[Quill.Postgres[SnakeCase], Nothing, TransactionEventReader] =
    ZLayer.fromFunction(new PostgresTransactionEventReader(_))
}
