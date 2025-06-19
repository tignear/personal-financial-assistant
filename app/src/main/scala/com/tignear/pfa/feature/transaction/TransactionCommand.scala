package com.tignear.pfa.feature.transaction

import com.tignear.pfa.core.Types.UserId
import java.time.Instant
import zio._
import com.tignear.pfa.core.InfraStructureError

// Command ADT and command types
sealed trait TransactionCommand
case class TransactionExpenseCommand(
  userId: UserId,
  amount: Long,
  transactionDate: Instant
) extends TransactionCommand

sealed trait TransactionCommandError
object TransactionCommandError {
  case object InvalidAmountError extends TransactionCommandError
}

// Aggregate and usecase (command side)
object TransactionAggregate {
  def handleCommand(
    command: TransactionCommand
  ): Either[TransactionCommandError, TransactionEvent] = command match {
    case expenseCmd: TransactionExpenseCommand =>
      if (expenseCmd.amount < 0) Left(TransactionCommandError.InvalidAmountError)
      else Right(TransactionExpenseEvent(expenseCmd.userId, expenseCmd.amount, expenseCmd.transactionDate))
  }
}

trait TransactionEventStore {
  def save(event: TransactionEvent): ZIO[Any, InfraStructureError, Unit]
}

class TransactionUsecase(store: TransactionEventStore) {
  def expense(
    userId: UserId,
    amount: Long,
    transactionDate: Instant
  ): ZIO[Any, TransactionCommandError | InfraStructureError, Unit] = {
    val command = TransactionExpenseCommand(userId, amount, transactionDate)
    ZIO.fromEither(TransactionAggregate.handleCommand(command)).flatMap(store.save)
  }
}
object TransactionUsecase {
  val layer: ZLayer[TransactionEventStore, Nothing, TransactionUsecase] =
    ZLayer.fromFunction(new TransactionUsecase(_))
}
