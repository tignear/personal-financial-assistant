package com.tignear.pfa.feature.transaction.postgres

import com.tignear.pfa.core.Types.UserId
import io.getquill.jdbczio.Quill
import com.tignear.pfa.feature.transaction.TransactionEventStore
import java.time.Instant
import io.getquill._
import com.tignear.pfa.infrastructure.EventRow
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder
import com.tignear.pfa.core.InfraStructureError
import com.tignear.pfa.feature.transaction.TransactionEvent
import zio.json.DeriveJsonDecoder
import zio.ZIO
import zio.ZLayer
import zio.Schedule

case class PostgresTransactionEventPayload(
    account: UserId,
    amount: Long,
    transactionDate: Instant
)

class PostgresTransactionEventStore(ctx: Quill.Postgres[SnakeCase])
    extends TransactionEventStore {
  import ctx._
  inline def schema =
    querySchema[EventRow[PostgresTransactionEventPayload]]("event")
  implicit val eventJsonbEncoder: JsonEncoder[PostgresTransactionEventPayload] =
    DeriveJsonEncoder.gen[PostgresTransactionEventPayload]
  implicit val eventJsonbDecoder: JsonDecoder[PostgresTransactionEventPayload] =
    DeriveJsonDecoder.gen[PostgresTransactionEventPayload]
  def save(event: TransactionEvent): ZIO[Any, InfraStructureError, Unit] = {
    val streamType = "transaction"
    val streamId = event.userId
    val eventType = event.eventType
    inline def selectMaxVersion = quote {
      schema
        .filter(e =>
          e.stream_type == lift(streamType) && e.stream_id == lift(streamId)
        )
        .map(_.version)
        .max
    }
    def tryInsert: ZIO[Any, Throwable, Boolean] = for {
      maxVersionOpt <- ctx.run(selectMaxVersion)
      nextVersion = maxVersionOpt.getOrElse(0L) + 1L
      row = EventRow(
        stream_type = streamType,
        stream_id = streamId,
        event_type = eventType,
        payload = io.getquill.JsonbValue(
          PostgresTransactionEventPayload(
            account = event.userId,
            amount = event.amount,
            transactionDate = event.transactionDate
          )
        ),
        version = nextVersion,
        user_id = Some(event.userId)
      )
      count <- run(schema.insertValue(lift(row)).onConflictIgnore)
    } yield count > 0
    tryInsert
      .repeat(
        Schedule.recurWhile((inserted: Boolean) => !inserted) && Schedule
          .recurs(4)
      )
      .unit
      .mapError(e => InfraStructureError.DatabaseError(e))
  }
}
object PostgresTransactionEventStore {
  val layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, TransactionEventStore] =
    ZLayer.fromFunction(new PostgresTransactionEventStore(_))
}