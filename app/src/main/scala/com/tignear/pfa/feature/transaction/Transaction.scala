package com.tignear.pfa.feature.transaction

import com.tignear.pfa.core.Types.UserId;
import com.tignear.pfa.core.{Command, Event};
import java.time.Instant
import com.tignear.pfa.core.InfraStructureError;
import zio._

sealed trait TransactionEvent extends Event
case class TransactionExpenseEvent(
    userId: UserId,
    amount: Long,
    transactionDate: Instant
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
