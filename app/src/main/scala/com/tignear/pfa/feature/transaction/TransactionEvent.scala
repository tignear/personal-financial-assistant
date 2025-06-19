package com.tignear.pfa.feature.transaction



import java.time.Instant
import com.tignear.pfa.core.Types.UserId

// Event ADT and event types (shared by command and query sides)
sealed trait TransactionEvent {
  def transactionDate: Instant
  def userId: UserId
  def amount: Long
  def eventType: String
}
case class TransactionExpenseEvent(
  userId: UserId,
  amount: Long,
  transactionDate: Instant
) extends TransactionEvent {
  def eventType: String = "TransactionExpenseEvent"
}
// Add more event types as needed
