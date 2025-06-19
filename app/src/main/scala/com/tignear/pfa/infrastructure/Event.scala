package com.tignear.pfa.infrastructure

import io.getquill.MappedEncoding
import io.getquill.JsonbValue

case class Event[T](
    stream_type: String,
    stream_id: String,
    event_type: String,
    payload: JsonbValue[T],
    version: Long,
    user_id: Option[String]
)
