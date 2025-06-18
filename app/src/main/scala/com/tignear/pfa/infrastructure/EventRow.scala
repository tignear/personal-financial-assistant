package com.tignear.pfa.infrastructure

import io.getquill.MappedEncoding
import io.circe.Json

case class EventRow(
    stream_type: String,
    stream_id: String,
    event_type: String,
    payload: Json,
    version: Long,
    user_id: Option[Long]
)

object EventRow {
  // Quill用のMappedEncoding: io.circe.Json <-> String
  implicit val encodeJson: MappedEncoding[Json, String] =
    MappedEncoding[Json, String](_.noSpaces)
  implicit val decodeJson: MappedEncoding[String, Json] =
    MappedEncoding[String, Json](io.circe.parser.parse(_).getOrElse(Json.Null))
}
