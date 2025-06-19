package com.tignear.pfa.feature.transaction

/** TransactionSpec tests the business rules for transaction command and usecase
  * handling:
  *
  *   - Only positive amounts are accepted for expense commands; non-positive
  *     amounts yield InvalidAmountError.
  *   - TransactionAggregate.handleCommand returns the correct event or error
  *     for each command.
  *   - TransactionUsecase.expense persists events using the event store, or
  *     returns errors as appropriate.
  *   - No event is saved if the command is invalid.
  *   - InfraStructureError is propagated if event saving fails.
  *   - Multiple events for the same or different users are saved and ordered
  *     correctly.
  *   - All event persistence is isolated per user.
  *
  * These tests ensure correctness, error handling, and persistence logic for
  * the transaction feature.
  */

import zio.test._
import zio.test.Assertion._
import java.time.Instant
import java.util.UUID
import zio._
import zio.stm._
import com.tignear.pfa.core.Types._
import com.tignear.pfa.feature.transaction._
import com.tignear.pfa.core.InfraStructureError
import io.getquill.jdbczio.Quill
import io.getquill._
import com.typesafe.config.ConfigFactory
import java.io.File
import io.getquill.jdbczio.Quill
import com.tignear.pfa.infrastructure.Event
import com.tignear.pfa.feature.transaction.postgres.PostgresTransactionEventStore
import com.tignear.pfa.feature.transaction.postgres.PostgresTransactionEventPayload

object TransactionSpec extends ZIOSpecDefault {
  case class StoredTransactionEvent(
      event: TransactionEvent,
      version: Long
  )

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
      eventsRef: TRef[Map[UserId, List[StoredTransactionEvent]]],
      versionsRef: TRef[Map[UserId, Long]],
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
            for {
              currentVersion <- versionsRef.get.map(
                _.getOrElse(event.userId, 0L)
              )
              newVersion = currentVersion + 1
              storedEvent = StoredTransactionEvent(event, newVersion)

              _ <- eventsRef.update(m =>
                m.updated(
                  event.userId,
                  storedEvent :: m.getOrElse(event.userId, List.empty)
                )
              )
              _ <- versionsRef.update(m => m.updated(event.userId, newVersion))
            } yield ()
          }
        }
      }.unit

      /** Sets the flag to determine if the next save operation should fail.
        * @param fail
        *   True to make save fail, false otherwise.
        * @return
        *   A ZIO effect that completes when the flag is set.
        */
    def setShouldFailSave(fail: Boolean): ZIO[Any, Nothing, Unit] =
      STM.atomically(shouldFailSaveRef.set(fail))

    def getStoredEventsForUser(
        userId: UserId
    ): ZIO[Any, Nothing, List[StoredTransactionEvent]] =
      STM.atomically {
        eventsRef.get.map(_.getOrElse(userId, List.empty).sortBy(_.version))
      }

    def getEventsForUser(
        userId: UserId
    ): ZIO[Any, Nothing, List[TransactionEvent]] =
      getStoredEventsForUser(userId).map(_.map(_.event))

    def getAllStoredEvents(): ZIO[Any, Nothing, List[StoredTransactionEvent]] =
      STM.atomically {
        eventsRef.get.map(
          _.values.flatten.toList.sortBy(e => (e.event.userId, e.version))
        )
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
            events <- TRef.make(Map.empty[UserId, List[StoredTransactionEvent]])
            versions <- TRef.make(Map.empty[UserId, Long])
            failFlag <- TRef.make(false) // Initial state is not to fail
          } yield new InMemoryTransactionEventStore(events, versions, failFlag)
        }
      )
  }

  // Define a ZLayer for TransactionUsecase, which depends on TransactionEventStore.
  // We assume TransactionUsecase is defined elsewhere and takes TransactionEventStore as a constructor parameter.

  // Suite for TransactionAggregate direct behavior
  val suite1 = suite("TransactionAggregate behavior")(
    test("should record a new expense when RecordExpenseCommand is valid") {
      val userId = "1673"
      val transactionDate = Instant.now()

      val command = TransactionExpenseCommand(
        amount = 1500L,
        transactionDate = transactionDate,
        userId = userId
      )

      val result = TransactionAggregate.handleCommand(command)

      assert(result)(
        isRight(
          // Filter by type using isSubtype
          isSubtype[TransactionExpenseEvent](
            // In the isSubtype assertion block, directly access fields and
            // assert their results individually.
            hasField(
              "userId",
              (e: TransactionExpenseEvent) => e.userId,
              equalTo(userId)
            ) &&
              hasField(
                "amount",
                (e: TransactionExpenseEvent) => e.amount,
                equalTo(1500L)
              ) &&
              hasField(
                "transactionDate",
                (e: TransactionExpenseEvent) => e.transactionDate,
                equalTo(transactionDate)
              ) &&
              hasField(
                "eventType",
                (e: TransactionExpenseEvent) => e.eventType,
                equalTo("TransactionExpenseEvent")
              )
          )
        )
      )

    },
    test(
      "should return InvalidAmount error for non-positive amount in expense command"
    ) {
      val command = TransactionExpenseCommand(
        amount = -100L,
        transactionDate = Instant.now(),
        userId = "1673"
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
      val userId = "2001"
      val amount = 2500L
      val transactionDate = Instant.now()

      for {
        // Obtain services from the ZIO environment provided by layers
        usecase <- ZIO.service[TransactionUsecase]
        store <- ZIO.service[
          InMemoryTransactionEventStore
        ] // Access concrete store for getEvents()

        // Execute the expense usecase
        _ <- usecase.expense(userId, amount, transactionDate)

        // Retrieve saved events from the store
        savedEvents <- store.getAllStoredEvents()
      } yield assert(savedEvents)(
        hasSize(equalTo(1)) &&
          // Assert on the first (and only) saved event
          hasFirst(
            hasField(
              "event",
              (e: StoredTransactionEvent) => e.event,
              isSubtype[TransactionExpenseEvent](
                hasField(
                  "userId",
                  (e: TransactionExpenseEvent) => e.userId,
                  equalTo(userId)
                ) &&
                  hasField(
                    "amount",
                    (e: TransactionExpenseEvent) => e.amount,
                    equalTo(amount)
                  ) &&
                  hasField(
                    "transactionDate",
                    (e: TransactionExpenseEvent) => e.transactionDate,
                    equalTo(transactionDate)
                  )
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
      val userId = "2002"
      val amount = -500L
      val transactionDate = Instant.now()

      for {
        usecase <- ZIO.service[TransactionUsecase]
        // Attempt to execute the expense usecase with an invalid amount
        actualError <- usecase.expense(userId, amount, transactionDate).flip
      } yield assert(actualError)(
        equalTo(TransactionCommandError.InvalidAmountError)
      )
    }.provide(
      InMemoryTransactionEventStore.createLayer() >>> TransactionUsecase.layer
    ),
    test("should not save any event when an expense command is invalid") {
      val userId = "2003"
      val amount = -100L // Invalid amount
      val transactionDate = Instant.now()

      for {
        usecase <- ZIO.service[TransactionUsecase]
        store <- ZIO.service[InMemoryTransactionEventStore]

        // Execute the expense usecase with an invalid amount; we expect it to fail.
        _ <- usecase.expense(userId, amount, transactionDate).ignore

        // Retrieve saved events from the store
        savedEvents <- store.getAllStoredEvents()
      } yield assert(savedEvents)(isEmpty) // Assert that no events were saved
    }.provide(
      InMemoryTransactionEventStore.createLayer(),
      TransactionUsecase.layer
    ),
    test("should propagate InfraStructureError when event saving fails") {
      val userId = "2004"
      val amount = 1000L
      val transactionDate = Instant.now()

      for {
        store <- ZIO.service[InMemoryTransactionEventStore]
        usecase <- ZIO.service[TransactionUsecase]

        _ <- store.setShouldFailSave(true) // Set the store to fail saves

        // Execute the expense usecase, expecting it to fail with InfraStructureError
        actualError <- usecase.expense(userId, amount, transactionDate).flip
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
      val userId1 = "3001"
      val amount1 = 100L
      val date1 = Instant.now()

      val userId2 = "3001" // Same user, different transaction
      val amount2 = 200L
      val date2 = Instant.now().plusSeconds(1) // Slightly later

      val userId3 = "3002" // Different user
      val amount3 = 50L
      val date3 = Instant.now().plusSeconds(2)

      for {
        usecase <- ZIO.service[TransactionUsecase]
        store <- ZIO.service[InMemoryTransactionEventStore]

        _ <- usecase.expense(userId1, amount1, date1)
        _ <- usecase.expense(userId2, amount2, date2)
        _ <- usecase.expense(userId3, amount3, date3)

        savedEvents <- store.getAllStoredEvents()
      } yield assert(savedEvents)(
        hasSize(equalTo(3)) &&
          hasAt(0)(
            hasField(
              "event",
              (e: StoredTransactionEvent) => e.event,
              isSubtype[TransactionExpenseEvent](
                hasField(
                  "userId",
                  (e: TransactionExpenseEvent) => e.userId,
                  equalTo(userId1)
                ) &&
                  hasField(
                    "amount",
                    (e: TransactionExpenseEvent) => e.amount,
                    equalTo(amount1)
                  ) &&
                  hasField(
                    "transactionDate",
                    (e: TransactionExpenseEvent) => e.transactionDate,
                    equalTo(date1)
                  )
              )
            )
          ) &&
          hasAt(1)(
            hasField(
              "event",
              (e: StoredTransactionEvent) => e.event,
              isSubtype[TransactionExpenseEvent](
                hasField(
                  "userId",
                  (e: TransactionExpenseEvent) => e.userId,
                  equalTo(userId2)
                ) &&
                  hasField(
                    "amount",
                    (e: TransactionExpenseEvent) => e.amount,
                    equalTo(amount2)
                  ) &&
                  hasField(
                    "transactionDate",
                    (e: TransactionExpenseEvent) => e.transactionDate,
                    equalTo(date2)
                  )
              )
            )
          ) &&
          hasAt(2)(
            hasField(
              "event",
              (e: StoredTransactionEvent) => e.event,
              isSubtype[TransactionExpenseEvent](
                hasField(
                  "userId",
                  (e: TransactionExpenseEvent) => e.userId,
                  equalTo(userId3)
                ) &&
                  hasField(
                    "amount",
                    (e: TransactionExpenseEvent) => e.amount,
                    equalTo(amount3)
                  ) &&
                  hasField(
                    "transactionDate",
                    (e: TransactionExpenseEvent) => e.transactionDate,
                    equalTo(date3)
                  )
              )
            )
          )
      )
    }
  ).provide(
    InMemoryTransactionEventStore.createLayer(),
    TransactionUsecase.layer
  )

  val config = ConfigFactory.load("application.conf")
  val dataSourceLayer: ZLayer[Any, Throwable, javax.sql.DataSource] =
    Quill.DataSource.fromConfig {
      config.getConfig("Quill.dataSource")
    }

  val testPostgresLayer
      : ZLayer[javax.sql.DataSource, Throwable, Quill.Postgres[SnakeCase]] =
    Quill.Postgres.fromNamingStrategy(SnakeCase)

  import java.time.Instant
  import com.tignear.pfa.core.Types.UserId

  def postgresEventStoreSpec =
    suite("PostgresTransactionEventStore integration")(
      test("should save an event with PostgresTransactionEventStore") {
        val userId = "9999"
        val event = TransactionExpenseEvent(userId, 1000L, Instant.now())
        for {
          _ <- ZIO.serviceWithZIO[Quill.Postgres[SnakeCase]] { ctx =>
            ctx.run(querySchema[Event[TransactionEvent]]("event").delete)
          }
          _ <- ZIO.serviceWithZIO[TransactionEventStore](_.save(event))
        } yield assertCompletes
      }
    ).provide(
      dataSourceLayer,
      testPostgresLayer,
      PostgresTransactionEventStore.layer
    )

  // Helper to clear the table once and generate a unique userId for all tests
  def integrationUserId(): String = java.util.UUID.randomUUID().toString
  lazy val clearTableOnce: ZIO[Quill.Postgres[SnakeCase], Throwable, Unit] =
    ZIO.serviceWithZIO[Quill.Postgres[SnakeCase]] { ctx =>
      import ctx._
      ctx
        .run(
          io.getquill
            .querySchema[Event[PostgresTransactionEventPayload]]("event")
            .delete
        )
        .unit
    }

  def postgresEventStoreAndReaderSpec =
    suite(
      "PostgresTransactionEventStore + PostgresTransactionEventReader integration"
    )(
      test("should save and read back an event using Postgres") {
        val userId = integrationUserId()
        val event = TransactionExpenseEvent(userId, 12345L, Instant.now())
        for {
          _ <- clearTableOnce
          _ <- ZIO.serviceWithZIO[TransactionEventStore](_.save(event))
          events <- ZIO.serviceWithZIO[TransactionEventReader](
            _.read(userId, None, None)
          )
        } yield assert(events.map(_.amount))(Assertion.contains(12345L))
      }
      // Add more tests here using integrationUserId
    ).provide(
      dataSourceLayer,
      testPostgresLayer,
      PostgresTransactionEventStore.layer,
      com.tignear.pfa.feature.transaction.postgres.PostgresTransactionEventReader.layer
    )

  def postgresEventReaderFilteringSpec =
    suite("PostgresTransactionEventReader filtering integration")(
      test("should filter events by from and to in Scala after DB fetch") {
        val userId = integrationUserId()
        val now = Instant.now()
        val event1 = TransactionExpenseEvent(
          userId,
          100L,
          now.minusSeconds(3600)
        ) // before
        val event2 = TransactionExpenseEvent(userId, 200L, now) // in range
        val event3 = TransactionExpenseEvent(
          userId,
          300L,
          now.plusSeconds(3600)
        ) // in range
        val event4 =
          TransactionExpenseEvent(userId, 400L, now.plusSeconds(7200)) // after
        for {
          _ <- clearTableOnce
          store <- ZIO.service[TransactionEventStore]
          reader <- ZIO.service[TransactionEventReader]
          _ <- store.save(event1)
          _ <- store.save(event2)
          _ <- store.save(event3)
          _ <- store.save(event4)
          // from = now, to = now + 3601 (should include event2 and event3)
          events <- reader.read(userId, Some(now), Some(now.plusSeconds(3601)))
        } yield {
          val amounts = events.map(_.amount)
          assert(amounts)(Assertion.hasSameElements(List(200L, 300L)))
        }
      }
    ).provide(
      dataSourceLayer,
      testPostgresLayer,
      PostgresTransactionEventStore.layer,
      com.tignear.pfa.feature.transaction.postgres.PostgresTransactionEventReader.layer
    )

  override def spec =
    suite("Transaction Features")(
      suite1,
      suite2,
      postgresEventStoreSpec,
      postgresEventStoreAndReaderSpec,
      postgresEventReaderFilteringSpec
    )
}
