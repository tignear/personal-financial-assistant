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
import com.tignear.pfa.feature.TransactionUsecase

object TransactionSpec extends ZIOSpecDefault {

  /** An in-memory implementation of TransactionEventStore for testing purposes.
    * It stores events in a TRef, making it safe for concurrent access within
    * ZIO STM. Now includes a method to retrieve all saved events for
    * assertions, and a flag to simulate save failures.
    * @param eventsRef
    *   A TRef holding the list of recorded TransactionEvent.
    * @param shouldFailSaveRef
    *   A TRef to control whether the save operation should fail.
    */
  class InMemoryTransactionEventStore(
      eventsRef: TRef[List[TransactionEvent]],
      shouldFailSaveRef: TRef[Boolean]
  ) extends TransactionEventStore {

    /** Saves a TransactionEvent to the in-memory store. If `shouldFailSaveRef`
      * is true, it fails with an InfraStructureError.
      * @param event
      *   The event to save.
      * @return
      *   A ZIO effect that completes when the event is saved, or fails with
      *   InfraStructureError.
      */
    override def save(
        event: TransactionEvent
    ): ZIO[Any, InfraStructureError, Unit] =
      STM.atomically {
        shouldFailSaveRef.get.flatMap { shouldFail =>
          if (shouldFail) {
            STM.fail(
              InfraStructureError.DatabaseError(
                new IllegalStateException("inmemory store error")
              )
            )
          } else {
            eventsRef.update(event :: _)
          }
        }
      }.unit

    /** Sets the flag to determine if the next save operation should fail.
      * @param fail
      *   True to make save fail, false otherwise.
      * @return
      *   A ZIO effect that completes when the flag is set.
      */
    def setShouldFailSave(fail: Boolean): UIO[Unit] =
      STM.atomically(shouldFailSaveRef.set(fail))

    /** Retrieves all events currently stored in the in-memory store.
      * @return
      *   A ZIO effect that returns a list of all TransactionEvent.
      */
    def getEvents(): ZIO[Any, Nothing, List[TransactionEvent]] =
      STM.atomically {
        eventsRef.get.map(_.reverse) // Reverse to get chronological order
      }
  }

  object InMemoryTransactionEventStore {

    /** Creates a ZLayer for the InMemoryTransactionEventStore. This allows for
      * easy dependency injection in tests.
      * @return
      *   A ZLayer providing the concrete InMemoryTransactionEventStore type,
      *   allowing access to methods like `getEvents` and `setShouldFailSave`.
      */
    def createLayer(): ZLayer[Any, Nothing, InMemoryTransactionEventStore] =
      ZLayer.fromZIO(
        STM.atomically {
          for {
            events <- TRef.make(List.empty[TransactionEvent])
            failFlag <- TRef.make(false) // Initial state is not to fail
          } yield new InMemoryTransactionEventStore(events, failFlag)
        }
      )
  }

  // Define a ZLayer for TransactionUsecase, which depends on TransactionEventStore.
  // We assume TransactionUsecase is defined elsewhere and takes TransactionEventStore as a constructor parameter.

  // Suite for TransactionAggregate direct behavior
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
          // Filter by type using isSubtype
          isSubtype[TransactionExpenceRecord](
            // In the isSubtype assertion block, directly access fields and
            // assert their results individually.
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

  // Suite for TransactionUsecase behavior, utilizing the in-memory store
  val suite2 = suite("TransactionUsecase behavior")(
    test("should successfully record an expense and save the event") {
      val userId = UserId(2001)
      val amount = 2500L
      val transactionDate = Instant.now()

      for {
        // Obtain services from the ZIO environment provided by layers
        usecase <- ZIO.service[TransactionUsecase]
        store <- ZIO.service[
          InMemoryTransactionEventStore
        ] // Access concrete store for getEvents()

        // Execute the expense usecase
        _ <- usecase.expence(userId, amount, transactionDate)

        // Retrieve saved events from the store
        savedEvents <- store.getEvents()
      } yield assert(savedEvents)(
        hasSize(equalTo(1)) &&
          // Assert on the first (and only) saved event
          hasFirst(
            isSubtype[TransactionExpenceRecord](
              hasField(
                "userId",
                (e: TransactionExpenceRecord) => e.userId,
                equalTo(userId)
              ) &&
                hasField(
                  "amount",
                  (e: TransactionExpenceRecord) => e.amount,
                  equalTo(amount)
                ) &&
                hasField(
                  "transactionDate",
                  (e: TransactionExpenceRecord) => e.transactionDate,
                  equalTo(transactionDate)
                )
            )
          )
      )
    }.provide(
      InMemoryTransactionEventStore.createLayer(),
      TransactionUsecase.layer
    ), // Provide the combined layers

    test(
      "should return InvalidAmountError when expense amount is non-positive"
    ) {
      val userId = UserId(2002)
      val amount = -500L
      val transactionDate = Instant.now()

      for {
        usecase <- ZIO.service[TransactionUsecase]
        // Attempt to execute the expense usecase with an invalid amount
        actualError <- usecase.expence(userId, amount, transactionDate).flip
      } yield assert(actualError)(
        equalTo(TransactionCommandError.InvalidAmountError)
      )
    }.provide(
      InMemoryTransactionEventStore.createLayer() >>> TransactionUsecase.layer
    ),
    test("should not save any event when an expense command is invalid") {
      val userId = UserId(2003)
      val amount = -100L // Invalid amount
      val transactionDate = Instant.now()

      for {
        usecase <- ZIO.service[TransactionUsecase]
        store <- ZIO.service[InMemoryTransactionEventStore]

        // Execute the expense usecase with an invalid amount; we expect it to fail.
        _ <- usecase.expence(userId, amount, transactionDate).ignore

        // Retrieve saved events from the store
        savedEvents <- store.getEvents()
      } yield assert(savedEvents)(isEmpty) // Assert that no events were saved
    }.provide(
      InMemoryTransactionEventStore.createLayer(),
      TransactionUsecase.layer
    ),
    test("should propagate InfraStructureError when event saving fails") {
      val userId = UserId(2004)
      val amount = 1000L
      val transactionDate = Instant.now()

      for {
        store <- ZIO.service[InMemoryTransactionEventStore]
        usecase <- ZIO.service[TransactionUsecase]

        _ <- store.setShouldFailSave(true) // Set the store to fail saves

        // Execute the expense usecase, expecting it to fail with InfraStructureError
        actualError <- usecase.expence(userId, amount, transactionDate).flip
      } yield assert(actualError)(
        isSubtype[InfraStructureError.DatabaseError](
          hasField(
            "message",
            (e: InfraStructureError.DatabaseError) => e.cause.getMessage,
            equalTo("inmemory store error")
          )
        )
      )
    }.provide(
      InMemoryTransactionEventStore.createLayer(),
      TransactionUsecase.layer
    ),
    test("should correctly save multiple valid expense events in order") {
      val userId1 = UserId(3001)
      val amount1 = 100L
      val date1 = Instant.now()

      val userId2 = UserId(3001) // Same user, different transaction
      val amount2 = 200L
      val date2 = Instant.now().plusSeconds(1) // Slightly later

      val userId3 = UserId(3002) // Different user
      val amount3 = 50L
      val date3 = Instant.now().plusSeconds(2)

      for {
        usecase <- ZIO.service[TransactionUsecase]
        store <- ZIO.service[InMemoryTransactionEventStore]

        _ <- usecase.expence(userId1, amount1, date1)
        _ <- usecase.expence(userId2, amount2, date2)
        _ <- usecase.expence(userId3, amount3, date3)

        savedEvents <- store.getEvents()
      } yield assert(savedEvents)(
        hasSize(equalTo(3)) &&
          hasAt(0)(
            isSubtype[TransactionExpenceRecord](
              hasField(
                "userId",
                (e: TransactionExpenceRecord) => e.userId,
                equalTo(userId1)
              ) &&
                hasField(
                  "amount",
                  (e: TransactionExpenceRecord) => e.amount,
                  equalTo(amount1)
                ) &&
                hasField(
                  "transactionDate",
                  (e: TransactionExpenceRecord) => e.transactionDate,
                  equalTo(date1)
                )
            )
          ) &&
          hasAt(1)(
            isSubtype[TransactionExpenceRecord](
              hasField(
                "userId",
                (e: TransactionExpenceRecord) => e.userId,
                equalTo(userId2)
              ) &&
                hasField(
                  "amount",
                  (e: TransactionExpenceRecord) => e.amount,
                  equalTo(amount2)
                ) &&
                hasField(
                  "transactionDate",
                  (e: TransactionExpenceRecord) => e.transactionDate,
                  equalTo(date2)
                )
            )
          ) &&
          hasAt(2)(
            isSubtype[TransactionExpenceRecord](
              hasField(
                "userId",
                (e: TransactionExpenceRecord) => e.userId,
                equalTo(userId3)
              ) &&
                hasField(
                  "amount",
                  (e: TransactionExpenceRecord) => e.amount,
                  equalTo(amount3)
                ) &&
                hasField(
                  "transactionDate",
                  (e: TransactionExpenceRecord) => e.transactionDate,
                  equalTo(date3)
                )
            )
          )
      )
    }
  ).provide(
    InMemoryTransactionEventStore.createLayer(),
    TransactionUsecase.layer
  )

  override def spec = suite("Transaction Features")(suite1, suite2)
}
