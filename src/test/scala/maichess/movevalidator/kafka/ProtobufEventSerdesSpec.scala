package maichess.movevalidator.kafka

import maichess.events.v1.match_events.*
import org.apache.kafka.common.header.internals.RecordHeaders
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import zio.test.*

// Round-trips the ScalaPB-generated maichess.events.v1 types through the
// zio-kafka Protobuf Serde (serialize -> deserialize) for the match.events
// payloads the validator handles: MoveSubmitted (consumed) and
// MoveValidated / MoveRejected (produced).
object ProtobufEventSerdesSpec extends ZIOSpecDefault:

  private val topic = "test"
  private def headers = new RecordHeaders()

  private def roundTrips[A <: GeneratedMessage](
      companion: GeneratedMessageCompanion[A],
      msg: A,
  ) =
    val serde = ProtobufEventSerdes.serde(companion)
    for
      bytes <- serde.serialize(topic, headers, msg)
      back  <- serde.deserialize(topic, headers, bytes)
    yield assertTrue(back == msg)

  private def env(payload: MatchEvent.Payload): MatchEvent =
    MatchEvent(
      eventId = "ev1",
      eventType = "match.event",
      aggregateId = "m1",
      sequence = 2L,
      occurredAt = 1_700_000_000_000L,
      producer = "move-validator-service",
      payload = payload,
    )

  def spec = suite("ProtobufEventSerdes")(
    test("MatchEvent MoveSubmitted (with position_history) round-trips") {
      roundTrips(
        MatchEvent,
        env(MatchEvent.Payload.MoveSubmitted(
          MoveSubmitted(
            moveUci = "e2e4",
            by = Some(Player(Player.Identity.UserId("w"))),
            fen = "fen",
            positionHistory = Seq("fen0", "fen1"),
          ),
        )),
      )
    },
    test("MatchEvent MoveValidated round-trips") {
      roundTrips(
        MatchEvent,
        env(MatchEvent.Payload.MoveValidated(
          MoveValidated(
            resultingFen = "fen2",
            gameResult = GameResult.GAME_RESULT_UNSPECIFIED,
            positionHistory = Seq("fen0", "fen1", "fen2"),
          ),
        )),
      )
    },
    test("MatchEvent MoveValidated with a terminal result round-trips") {
      roundTrips(
        MatchEvent,
        env(MatchEvent.Payload.MoveValidated(
          MoveValidated(resultingFen = "fen3", gameResult = GameResult.GAME_RESULT_WHITE_WON),
        )),
      )
    },
    test("MatchEvent MoveRejected round-trips") {
      roundTrips(
        MatchEvent,
        env(MatchEvent.Payload.MoveRejected(MoveRejected(moveUci = "e2e5", reason = "illegal"))),
      )
    },
  )
