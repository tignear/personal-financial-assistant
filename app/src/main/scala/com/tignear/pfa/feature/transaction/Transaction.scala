package com.tignear.pfa.feature.transaction

import com.tignear.pfa.core.Types.UserId;
import com.tignear.pfa.core.{Command, Event};
import java.time.Instant
import com.tignear.pfa.core.InfraStructureError;
import zio._

sealed trait TransactionEvent extends Event
case class TransactionExpenceEvent(
    userId: UserId,
    amount: Long,
    transactionDate: Instant
) extends TransactionEvent {
  def eventType: String = "TransactionExpenceEvent"
}
sealed trait TransactionCommand extends Command
case class TransactionExpenceCommand(
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
    case expenseCmd: TransactionExpenceCommand =>
      if (expenseCmd.amount < 0) {
        Left(TransactionCommandError.InvalidAmountError)
      } else {
        Right(
          TransactionExpenceEvent(
            amount = expenseCmd.amount,
            userId = expenseCmd.userId,
            transactionDate = expenseCmd.transactionDate
          )
        )
      }
  }

trait TransactionEventStore:
  def save(event: TransactionEvent): ZIO[Any, InfraStructureError, Unit]

type TransactionUsecaseError = TransactionCommandError | InfraStructureError;
class TransactionUsecase(store: TransactionEventStore):
  def expence(
      userId: UserId,
      amount: Long,
      transactionDate: Instant
  ): ZIO[Any, TransactionUsecaseError, Unit] = {
    val command = TransactionExpenceCommand(
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
