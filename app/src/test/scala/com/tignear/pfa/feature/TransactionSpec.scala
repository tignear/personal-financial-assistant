package com.tignear.pfa.feature.transaction

import zio.test._
import zio.test.Assertion._
import java.time.Instant
import java.util.UUID
import zio._
import zio.stm._
import com.tignear.pfa.core.Types._
import com.tignear.pfa.feature.TransactionEvent
import com.tignear.pfa.feature.TransactionEvent._
import com.tignear.pfa.feature.TransactionCommand
import com.tignear.pfa.feature.TransactionAggregate
import com.tignear.pfa.feature.TransactionCommandError
import com.tignear.pfa.feature.TransactionEventStore
import com.tignear.pfa.core.InfraStructureError

object TransactionSpec extends ZIOSpecDefault {
  class InMemoryTransactionEventStore(eventsRef: TRef[List[TransactionEvent]])
      extends TransactionEventStore {
    override def save(
        event: TransactionEvent
    ): ZIO[Any, InfraStructureError, Unit] =
      STM.atomically {
        eventsRef.update(event :: _)
      }.unit
  }
  object InMemoryTransactionEventStore {
    def createLayer(): ZLayer[Any, Nothing, TransactionEventStore] =
      ZLayer.fromZIO(
        STM.atomically(
          TRef
            .make(List.empty[TransactionEvent])
            .map(new InMemoryTransactionEventStore(_))
        )
      )
  }
  val suite1 = suite("TransactionAggregate behavior")(
    test("should record a new expence when RecordExpenceCommand is valid") {
      val userId = UserId(1673)
      val transactionDate = Instant.now()

      val command = TransactionCommand.Expence(
        amount = 1500L,
        transactionDate = transactionDate,
        userId = userId
      )

      val result = TransactionAggregate.handleCommand(command)

      assert(result)(
        isRight(
          // isSubtype で型を絞り込む
          isSubtype[TransactionExpenceRecord](
            // isSubtype のアサーションブロック内で、フィールドに直接アクセスし、
            // その結果を個別に assert する形に修正
            hasField(
              "userId",
              (e: TransactionExpenceRecord) => e.userId,
              equalTo(userId)
            ) &&
              hasField(
                "amount",
                (e: TransactionExpenceRecord) => e.amount,
                equalTo(1500L)
              ) &&
              hasField(
                "transactionDate",
                (e: TransactionExpenceRecord) => e.transactionDate,
                equalTo(transactionDate)
              ) &&
              hasField(
                "eventType",
                (e: TransactionExpenceRecord) => e.eventType,
                equalTo("TransactionEvent")
              )
          )
        )
      )

    },
    test(
      "should return InvalidAmount error for non-positive amount in expence command"
    ) {
      val command = TransactionCommand.Expence(
        amount = -100L,
        transactionDate = Instant.now(),
        userId = UserId(1673)
      )
      val result = TransactionAggregate.handleCommand(command)
      assert(result)(
        isLeft(equalTo(TransactionCommandError.InvalidAmountError))
      )
    }
  )

  override def spec = suite("Transaction Features")(suite1)
}
