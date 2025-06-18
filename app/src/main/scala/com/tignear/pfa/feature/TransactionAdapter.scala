package com.tignear.pfa.feature
import com.tignear.pfa.feature.TransactionEvent
import zio._
import zio.stm._

trait TransactionEventStore:
  def save(event: TransactionEvent): ZIO[Any, Throwable, Unit]


