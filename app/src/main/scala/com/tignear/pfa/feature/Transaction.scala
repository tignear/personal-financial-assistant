package com.tignear.pfa.feature

import com.tignear.pfa.core.Types.UserId;
import com.tignear.pfa.core.{Command, Event};
import java.time.Instant
import com.tignear.pfa.core.InfraStructureError;
import zio.ZIO

sealed trait TransactionEvent extends Event:
  override def eventType: String = "TransactionEvent"
object TransactionEvent:
  case class TransactionExpenceRecord(
      userId: UserId,
      amount: Long,
      transactionDate: Instant
  ) extends TransactionEvent

sealed trait TransactionCommand extends Command
object TransactionCommand:
  case class Expence(
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
    case expenseCmd: TransactionCommand.Expence =>
      if (expenseCmd.amount < 0) {
        Left(TransactionCommandError.InvalidAmountError)
      } else {
        Right(
          TransactionEvent.TransactionExpenceRecord(
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
    val command = TransactionCommand.Expence(
      amount = amount,
      transactionDate = transactionDate,
      userId = userId
    )
    TransactionAggregate.handleCommand(command) match {
      case Left(error) => {
        ZIO.fail(error)
      }
      case Right(event) => {
        store.save(event)
      }
    }
  }
