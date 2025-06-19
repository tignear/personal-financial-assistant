package com.tignear.pfa.feature.timeline

/** TimelineSpec tests the business rules for Timeline aggregation:
  *
  *   - Only events for the specified user are included.
  *   - Only TransactionExpenseEvent is summed for expenses.
  *   - The time range is [from, to): from is inclusive, to is exclusive.
  *   - beforeAmount is the sum of all expenses strictly before 'from'.
  *   - totalExpense is the sum of all expenses in [from, to).
  *   - Events at 'from' are included, at 'to' are excluded.
  *   - Empty, all-before, all-after, and reversed range cases are handled.
  *   - Reversed range (from > to) results in a domain error
  *     (TimelineQueryError.InvalidRange).
  *
  * These tests ensure correctness, boundary handling, and user isolation for
  * the Timeline feature.
  */

import zio.test._
import zio.test.Assertion._
import zio._
import java.time.Instant
import com.tignear.pfa.core.Types.UserId
import com.tignear.pfa.core.InfraStructureError
import com.tignear.pfa.feature.timeline.Timeline
import com.tignear.pfa.feature.transaction.TransactionEvent
import com.tignear.pfa.feature.transaction.TransactionEventReader
import com.tignear.pfa.feature.transaction.TransactionExpenseEvent

object TimelineSpec extends ZIOSpecDefault {
  // Dummy TransactionEventReader for testing
  class DummyReader(events: List[TransactionEvent])
      extends TransactionEventReader {
    override def read(
        userId: UserId,
        from: Option[Instant],
        to: Option[Instant]
    ): ZIO[Any, InfraStructureError, List[TransactionEvent]] =
      ZIO.succeed(
        events
          .filter(e => e.userId == userId)
          .filter(e =>
            from.forall(f => !e.transactionDate.isBefore(f))
          ) // from inclusive
          .filter(e =>
            to.forall(t => e.transactionDate.isBefore(t))
          ) // to exclusive
      )
  }

  val userId = "1"
  val otherUserId = "2"
  val base = Instant.parse("2025-06-01T00:00:00Z")

  // Helper to sum amounts in a list of events
  def sumAmounts(events: List[TransactionEvent]): Long = events.collect {
    case e: TransactionExpenseEvent => e.amount
  }.sum

  // Helper to create a TransactionExpenseEvent for this user
  def exp(
      amount: Long,
      at: Instant,
      uid: UserId = userId
  ): TransactionExpenseEvent =
    TransactionExpenseEvent(uid, amount, at)

  // Helper to check if all events are within [from, to)
  def allInRange(
      events: List[TransactionEvent],
      from: Instant,
      to: Instant
  ): Boolean =
    events.forall(e =>
      !e.transactionDate.isBefore(from) && e.transactionDate.isBefore(to)
    )

  override def spec = suite("TimelineSpec")(
    // Test: Normal case with before, at, and in-range events
    test(
      "getTimeline: calculates beforeAmount and totalExpense correctly for normal case"
    ) {
      // This test checks that beforeAmount and totalExpense are correct for typical event distribution.
      val from = base
      val to = base.plusSeconds(7201)
      val testEvents = List(
        exp(100, base.minusSeconds(3600)), // before
        exp(200, base.plusSeconds(3600)), // in range
        exp(300, base.plusSeconds(7200)), // in range
        exp(111, base) // exactly at 'from'
      )
      val reader = new DummyReader(testEvents)
      val timeline = new Timeline(reader)
      for {
        _ <- ZIO.attempt(assert(from.isBefore(to)))
        result <- timeline.getTimeline(userId, from, to)
      } yield {
        val (summary, filteredEvents) = result
        val expectedBefore = testEvents.filter(_.transactionDate.isBefore(from))
        val expectedInRange = testEvents.filter(e =>
          !e.transactionDate.isBefore(from) && e.transactionDate.isBefore(to)
        )
        assert(summary.beforeAmount)(equalTo(sumAmounts(expectedBefore))) &&
        assert(summary.totalExpense)(equalTo(sumAmounts(expectedInRange))) &&
        assert(filteredEvents)(equalTo(expectedInRange)) &&
        assert(allInRange(filteredEvents, from, to))(isTrue) &&
        assert(summary.totalExpense)(equalTo(sumAmounts(filteredEvents)))
      }
    },
    // Test: Boundary inclusion/exclusion for 'from' and 'to'
    test("getTimeline: includes event at 'from', excludes event at 'to'") {
      // This test checks that an event exactly at 'from' is included, and at 'to' is excluded.
      val from = base
      val to = base.plusSeconds(7201)
      val eventAtTo = exp(222, to)
      val eventAtFrom = exp(111, from)
      val testEvents = List(
        exp(200, base.plusSeconds(3600)),
        exp(300, base.plusSeconds(7200)),
        eventAtFrom,
        eventAtTo
      )
      val reader = new DummyReader(testEvents)
      val timeline = new Timeline(reader)
      for {
        _ <- ZIO.attempt(assert(from.isBefore(to)))
        result <- timeline.getTimeline(userId, from, to)
      } yield {
        val (summary, filteredEvents) = result
        assert(
          filteredEvents.exists(e =>
            e.amount == 111 && e.transactionDate == from
          )
        )(isTrue) &&
        assert(
          filteredEvents.exists(e => e.amount == 222 && e.transactionDate == to)
        )(isFalse) &&
        assert(allInRange(filteredEvents, from, to))(isTrue) &&
        assert(summary.totalExpense)(equalTo(200 + 300 + 111))
      }
    },
    // Test: All events after 'to'
    test(
      "getTimeline: all events after 'to' yields zero summary and empty events"
    ) {
      // This test checks that if all events are after 'to', both beforeAmount and totalExpense are zero.
      val from = base
      val to = base.plusSeconds(7201)
      val afterEvents = List(
        exp(10, to.plusSeconds(1)),
        exp(20, to.plusSeconds(100))
      )
      val reader = new DummyReader(afterEvents)
      val timeline = new Timeline(reader)
      for {
        result <- timeline.getTimeline(userId, from, to)
      } yield {
        val (summary, filteredEvents) = result
        assert(summary.beforeAmount)(equalTo(0L)) &&
        assert(summary.totalExpense)(equalTo(0L)) &&
        assert(filteredEvents)(isEmpty)
      }
    },
    // Test: All events before 'from'
    test(
      "getTimeline: all events before 'from' yields beforeAmount > 0 and totalExpense == 0"
    ) {
      // This test checks that if all events are before 'from', beforeAmount is sum of all, totalExpense is zero.
      val from = base
      val to = base.plusSeconds(7201)
      val beforeEvents = List(
        exp(10, base.minusSeconds(10000)),
        exp(20, base.minusSeconds(5000))
      )
      val reader = new DummyReader(beforeEvents)
      val timeline = new Timeline(reader)
      for {
        result <- timeline.getTimeline(userId, from, to)
      } yield {
        val (summary, filteredEvents) = result
        assert(summary.beforeAmount)(equalTo(10 + 20)) &&
        assert(summary.totalExpense)(equalTo(0L)) &&
        assert(filteredEvents)(isEmpty)
      }
    },
    // Test: Empty event list
    test(
      "getTimeline: empty event list returns zero summary and empty events"
    ) {
      // This test checks that an empty event list returns zero for all summary fields.
      val reader = new DummyReader(Nil)
      val timeline = new Timeline(reader)
      val from = base
      val to = base.plusSeconds(7201)
      for {
        result <- timeline.getTimeline(userId, from, to)
      } yield {
        val (summary, filteredEvents) = result
        assert(summary.beforeAmount)(equalTo(0L)) &&
        assert(summary.totalExpense)(equalTo(0L)) &&
        assert(filteredEvents)(isEmpty)
      }
    },
    // Test: Multi-user filtering
    test("getTimeline: only events for the specified user are included") {
      // This test checks that events for other users are ignored.
      val from = base
      val to = base.plusSeconds(7201)
      val testEvents = List(
        exp(100, base.minusSeconds(3600)), // userId 1, before
        exp(200, base.plusSeconds(3600)), // userId 1, in range
        exp(999, base.plusSeconds(3600), otherUserId), // userId 2, in range
        exp(300, base.plusSeconds(7200)) // userId 1, in range
      )
      val reader = new DummyReader(testEvents)
      val timeline = new Timeline(reader)
      for {
        result <- timeline.getTimeline(userId, from, to)
      } yield {
        val (summary, filteredEvents) = result
        assert(filteredEvents.forall(_.userId == userId))(isTrue) &&
        assert(summary.totalExpense)(equalTo(200 + 300))
      }
    },
    // Test: Reversed range should return TimelineQueryError.InvalidRange
    test(
      "getTimeline: reversed range (from > to) returns TimelineQueryError.InvalidRange"
    ) {
      // This test checks that a reversed range returns a domain error, not a fatal error.
      val from = base.plusSeconds(7201)
      val to = base
      val testEvents = List(
        exp(100, base.minusSeconds(3600)),
        exp(200, base.plusSeconds(3600)),
        exp(300, base.plusSeconds(7200)),
        exp(111, base),
        exp(222, from)
      )
      val reader = new DummyReader(testEvents)
      val timeline = new Timeline(reader)
      val effect = timeline.getTimeline(userId, from, to).either
      effect.map {
        case Left(TimelineQueryError.InvalidRange) => assertCompletes
        case Left(other) => assert(false)(isTrue) // Unexpected error
        case Right(_)    => assert(false)(isTrue) // Should not succeed
      }
    }
  )
}
