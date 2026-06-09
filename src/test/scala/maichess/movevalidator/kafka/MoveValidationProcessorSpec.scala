package maichess.movevalidator.kafka

import maichess.events.v1.match_events.{
  GameResult as ProtoGameResult,
  MatchEvent,
  MoveSubmitted,
  MoveValidated,
  Player,
}
import maichess.movevalidator.domain.GameResult
import maichess.movevalidator.service.{ValidatorService, ValidatorServiceLive}
import zio.ZIO
import zio.test.*

// Drives the pure stream-processor transform: a consumed match.events MatchEvent
// carrying MoveSubmitted is mapped to MoveValidated / MoveRejected, threading the
// real ValidatorService. Asserts the envelope plumbing (causation, sequence,
// producer, ids copied through) and the game-result + position_history mapping.
object MoveValidationProcessorSpec extends ZIOSpecDefault:

  private val startFen        = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  // After 1. f3 e5 2. g4 — Black plays Qd8h4# next (fool's mate).
  private val preMateBlackFen = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2"
  private val backwardPawnFen = "8/8/8/8/8/8/4P3/4K2k w - - 0 1"
  private val kingVsKingFen   = "8/8/4k3/8/8/4K3/8/8 w - - 2 10"
  private val repeatedKey     = "8/8/4k3/8/8/5K2/8/8 b - -"

  private val NewEventId   = "generated-event-id"
  private val NewOccurred  = 1_700_000_999_000L

  private def processor = ZIO.serviceWith[ValidatorService](MoveValidationProcessor(_))

  private def submitted(moveUci: String, fen: String, history: Seq[String]): MatchEvent =
    MatchEvent(
      eventId = "source-event",
      eventType = "match.MoveSubmitted",
      aggregateId = "match-1",
      sequence = 5L,
      occurredAt = 111L,
      correlationId = "corr-1",
      causationId = "cause-0",
      producer = "match-manager-service",
      payload = MatchEvent.Payload.MoveSubmitted(
        MoveSubmitted(
          moveUci = moveUci,
          by = Some(Player(Player.Identity.UserId("white"))),
          fen = fen,
          positionHistory = history,
        ),
      ),
    )

  private def handle(event: MatchEvent) =
    processor.flatMap(_.handle(event, NewEventId, NewOccurred))

  def spec = suite("MoveValidationProcessorSpec")(
    test("legal move produces MoveValidated with envelope copied and a new id/time") {
      handle(submitted("e2e4", startFen, Nil)).map {
        case Some(event) =>
          event.payload match
            case MatchEvent.Payload.MoveValidated(validated) =>
              assertTrue(
                event.eventType == "match.MoveValidated",
                event.aggregateId == "match-1",
                event.causationId == "source-event",
                event.correlationId == "corr-1",
                event.sequence == 6L,
                event.producer == "move-validator-service",
                event.eventId == NewEventId,
                event.occurredAt == NewOccurred,
                validated.resultingFen.nonEmpty,
                validated.gameResult == ProtoGameResult.GAME_RESULT_UNSPECIFIED,
                validated.positionHistory.nonEmpty,
              )
            case other => assertTrue(false) ?? s"expected MoveValidated, got $other"
        case None => assertTrue(false) ?? "expected an emitted event"
      }
    },
    test("a reversible move carries the prior position_history through and appends") {
      handle(submitted("g1f3", startFen, Seq("h1", "h2"))).map {
        case Some(event) =>
          event.payload match
            case MatchEvent.Payload.MoveValidated(validated) =>
              assertTrue(
                validated.positionHistory.take(2) == Seq("h1", "h2"),
                validated.positionHistory.size == 3,
                validated.gameResult == ProtoGameResult.GAME_RESULT_UNSPECIFIED,
              )
            case other => assertTrue(false) ?? s"expected MoveValidated, got $other"
        case None => assertTrue(false) ?? "expected an emitted event"
      }
    },
    test("checkmating move produces MoveValidated with the terminal result") {
      handle(submitted("d8h4", preMateBlackFen, Nil)).map {
        case Some(event) =>
          event.payload match
            case MatchEvent.Payload.MoveValidated(validated) =>
              assertTrue(validated.gameResult == ProtoGameResult.GAME_RESULT_BLACK_WON)
            case other => assertTrue(false) ?? s"expected MoveValidated, got $other"
        case None => assertTrue(false) ?? "expected an emitted event"
      }
    },
    test("threefold repetition is reported on MoveValidated") {
      handle(submitted("e3f3", kingVsKingFen, Seq(repeatedKey, repeatedKey))).map {
        case Some(event) =>
          event.payload match
            case MatchEvent.Payload.MoveValidated(validated) =>
              assertTrue(validated.gameResult == ProtoGameResult.GAME_RESULT_THREEFOLD_REPETITION)
            case other => assertTrue(false) ?? s"expected MoveValidated, got $other"
        case None => assertTrue(false) ?? "expected an emitted event"
      }
    },
    test("an illegal move produces MoveRejected with the move and a reason") {
      handle(submitted("e2e1", backwardPawnFen, Nil)).map {
        case Some(event) =>
          event.payload match
            case MatchEvent.Payload.MoveRejected(rejected) =>
              assertTrue(
                event.eventType == "match.MoveRejected",
                event.causationId == "source-event",
                event.sequence == 6L,
                rejected.moveUci == "e2e1",
                rejected.reason.nonEmpty,
              )
            case other => assertTrue(false) ?? s"expected MoveRejected, got $other"
        case None => assertTrue(false) ?? "expected an emitted event"
      }
    },
    test("an unparseable FEN produces MoveRejected instead of failing the stream") {
      handle(submitted("e2e4", "not a valid fen", Nil)).map {
        case Some(event) =>
          event.payload match
            case MatchEvent.Payload.MoveRejected(rejected) =>
              assertTrue(rejected.moveUci == "e2e4", rejected.reason.nonEmpty)
            case other => assertTrue(false) ?? s"expected MoveRejected, got $other"
        case None => assertTrue(false) ?? "expected an emitted event"
      }
    },
    test("a non-MoveSubmitted event is ignored (no re-processing of own output)") {
      val ownOutput = submitted("e2e4", startFen, Nil).copy(
        payload = MatchEvent.Payload.MoveValidated(MoveValidated(resultingFen = startFen)),
      )
      handle(ownOutput).map(result => assertTrue(result.isEmpty))
    },
    test("an event with no payload is ignored") {
      val empty = submitted("e2e4", startFen, Nil).copy(payload = MatchEvent.Payload.Empty)
      handle(empty).map(result => assertTrue(result.isEmpty))
    },
    test("every domain GameResult maps to its proto enum") {
      assertTrue(
        MoveValidationProcessor.toProto(GameResult.None) == ProtoGameResult.GAME_RESULT_UNSPECIFIED,
        MoveValidationProcessor.toProto(GameResult.WhiteWon) == ProtoGameResult.GAME_RESULT_WHITE_WON,
        MoveValidationProcessor.toProto(GameResult.BlackWon) == ProtoGameResult.GAME_RESULT_BLACK_WON,
        MoveValidationProcessor.toProto(GameResult.Stalemate) == ProtoGameResult.GAME_RESULT_STALEMATE,
        MoveValidationProcessor.toProto(GameResult.FiftyMoveRule) == ProtoGameResult.GAME_RESULT_FIFTY_MOVE_RULE,
        MoveValidationProcessor.toProto(
          GameResult.InsufficientMaterial,
        ) == ProtoGameResult.GAME_RESULT_INSUFFICIENT_MATERIAL,
        MoveValidationProcessor.toProto(
          GameResult.ThreefoldRepetition,
        ) == ProtoGameResult.GAME_RESULT_THREEFOLD_REPETITION,
      )
    },
  ).provide(ValidatorServiceLive.layer)
