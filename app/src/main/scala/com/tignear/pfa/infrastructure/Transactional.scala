package com.tignear.pfa.infrastructure

import zio._
import com.tignear.pfa.core.InfraStructureError

trait Transactional {
  val aspect: ZIOAspect[
    Nothing,
    Any,
    Nothing,
    InfraStructureError,
    Nothing,
    Any
  ]
}

object Transactional {
  def aspect: ZIO[Transactional, Nothing, ZIOAspect[
    Nothing,
    Any,
    Nothing,
    InfraStructureError,
    Nothing,
    Any
  ]] =
    ZIO.serviceWith[Transactional](_.aspect)
}
