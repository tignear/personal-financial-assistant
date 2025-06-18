package com.tignear.pfa.feature.transaction

import com.tignear.pfa.core.Types.UserId;
import com.tignear.pfa.core.{Command, Event};
import java.time.Instant
import com.tignear.pfa.core.InfraStructureError;
import zio._
import com.tignear.pfa.infrastructure.EventRow
import io.getquill._
import io.circe.syntax.EncoderOps
import io.circe.generic.auto._
import io.getquill.jdbczio.Quill

sealed trait TransactionEvent extends Event {
  def transactionDate: Instant
  def userId: UserId
  def amount: Long
}
case class TransactionExpenseEvent(
    val userId: UserId,
    val amount: Long,
    val transactionDate: Instant
) extends TransactionEvent {
  def eventType: String = "TransactionExpenseEvent"
}
sealed trait TransactionCommand extends Command
case class TransactionExpenseCommand(
    userId: UserId,
    amount: Long,
    transactionDate: Instant
) extends TransactionCommand
sealed trait TransactionCommandError
object TransactionCommandError {
  case object InvalidAmountError extends TransactionCommandError
}

object TransactionAggregate:
  def handleCommand(
      command: TransactionCommand
  ): Either[TransactionCommandError, TransactionEvent] = command match {
    case expenseCmd: TransactionExpenseCommand =>
      if (expenseCmd.amount < 0) {
        Left(TransactionCommandError.InvalidAmountError)
      } else {
        Right(
          TransactionExpenseEvent(
            amount = expenseCmd.amount,
            userId = expenseCmd.userId,
            transactionDate = expenseCmd.transactionDate
          )
        )
      }
  }

trait TransactionEventStore:
  def save(event: TransactionEvent): ZIO[Any, InfraStructureError, Unit]

class PostgresTransactionEventStore(
    ctx: Quill.Postgres[SnakeCase]
) extends TransactionEventStore {
  import ctx._
  import com.tignear.pfa.infrastructure.EventRow._ // for MappedEncoding

  def save(
      event: TransactionEvent
  ): ZIO[Any, InfraStructureError, Unit] = {
    val streamType = "transaction"
    val streamId = event.userId.id.toString
    val eventType = event.eventType
    inline def selectMaxVersion = quote {
      query[EventRow]
        .filter(e =>
          e.stream_type == lift(streamType) && e.stream_id == lift(streamId)
        )
        .map(_.version)
        .max
    }
    def tryInsert: ZIO[Any, Throwable, Boolean] = for {
      maxVersionOpt <- ctx.run(selectMaxVersion)
      nextVersion = maxVersionOpt.getOrElse(0L) + 1L
      payloadJson = event.asJson
      row = EventRow(
        stream_type = streamType,
        stream_id = streamId,
        event_type = eventType,
        payload = payloadJson,
        version = nextVersion,
        user_id = Some(event.userId.id)
      )
      count <- run(
        querySchema[EventRow]("event").insertValue(lift(row)).onConflictIgnore
      )
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

type TransactionUsecaseError = TransactionCommandError | InfraStructureError;
class TransactionUsecase(store: TransactionEventStore):
  def expense(
      userId: UserId,
      amount: Long,
      transactionDate: Instant
  ): ZIO[Any, TransactionUsecaseError, Unit] = {
    val command = TransactionExpenseCommand(
      amount = amount,
      transactionDate = transactionDate,
      userId = userId
    )
    ZIO
      .fromEither(TransactionAggregate.handleCommand(command))
      .flatMap(store.save)
  }
object TransactionUsecase:
  val layer: ZLayer[TransactionEventStore, Nothing, TransactionUsecase] =
    ZLayer.fromFunction(new TransactionUsecase(_))

trait TransactionEventReader:
  // Contains from,and not contains to
  // If from is None, it means all events before 'to'
  // If to is None, it means all events after 'from'
  def read(
      userId: UserId,
      from: Option[Instant],
      to: Option[Instant]
  ): ZIO[Any, InfraStructureError, List[TransactionEvent]]
