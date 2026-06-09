package maichess.movevalidator.kafka

import maichess.events.v1.match_events.{
  GameResult as ProtoGameResult,
  MatchEvent,
  MoveRejected,
  MoveSubmitted,
  MoveValidated,
}
import maichess.movevalidator.domain.{Fen, GameResult, UciMove, ValidationResult}
import maichess.movevalidator.service.ValidatorService
import zio.{UIO, ZIO}

// Pure stream-processor decision logic: maps a consumed match.events.v1 MatchEvent
// to the MatchEvent the move-validator should emit. Only MoveSubmitted is acted
// on; every other payload (including the validator's own MoveValidated /
// MoveRejected output, which rides the same topic) yields None so the consumer
// advances past it without re-emitting. The opaque position_history blob is
// carried through unchanged — the validator owns it (threefold repetition).
final class MoveValidationProcessor(validator: ValidatorService):

  def handle(event: MatchEvent, eventId: String, occurredAt: Long): UIO[Option[MatchEvent]] =
    event.payload match
      case MatchEvent.Payload.MoveSubmitted(submitted) =>
        validate(event, submitted, eventId, occurredAt).map(Some(_))
      case _ =>
        ZIO.none

  private def validate(
      source: MatchEvent,
      submitted: MoveSubmitted,
      eventId: String,
      occurredAt: Long,
  ): UIO[MatchEvent] =
    validator
      .validateMove(Fen(submitted.fen), UciMove(submitted.moveUci), submitted.positionHistory.toList)
      .map {
        case ValidationResult.Valid(fen, result, history) =>
          validated(source, eventId, occurredAt, fen.value, result, history)
        case ValidationResult.Invalid(reason) =>
          rejected(source, submitted.moveUci, eventId, occurredAt, reason)
      }
      .catchAll(reason => ZIO.succeed(rejected(source, submitted.moveUci, eventId, occurredAt, reason)))

  private def validated(
      source: MatchEvent,
      eventId: String,
      occurredAt: Long,
      resultingFen: String,
      result: GameResult,
      history: List[String],
  ): MatchEvent =
    envelope(
      source,
      eventId,
      occurredAt,
      "match.MoveValidated",
      MatchEvent.Payload.MoveValidated(
        MoveValidated(resultingFen = resultingFen, gameResult = toProto(result), positionHistory = history),
      ),
    )

  private def rejected(
      source: MatchEvent,
      moveUci: String,
      eventId: String,
      occurredAt: Long,
      reason: String,
  ): MatchEvent =
    envelope(
      source,
      eventId,
      occurredAt,
      "match.MoveRejected",
      MatchEvent.Payload.MoveRejected(MoveRejected(moveUci = moveUci, reason = reason)),
    )

  private def envelope(
      source: MatchEvent,
      eventId: String,
      occurredAt: Long,
      eventType: String,
      payload: MatchEvent.Payload,
  ): MatchEvent =
    MatchEvent(
      eventId = eventId,
      eventType = eventType,
      aggregateId = source.aggregateId,
      sequence = source.sequence + 1,
      occurredAt = occurredAt,
      correlationId = source.correlationId,
      causationId = source.eventId,
      producer = MoveValidationProcessor.Producer,
      payload = payload,
    )

  private def toProto(result: GameResult): ProtoGameResult = MoveValidationProcessor.toProto(result)

object MoveValidationProcessor:
  val Producer = "move-validator-service"

  private[kafka] def toProto(result: GameResult): ProtoGameResult = result match
    case GameResult.None                 => ProtoGameResult.GAME_RESULT_UNSPECIFIED
    case GameResult.WhiteWon             => ProtoGameResult.GAME_RESULT_WHITE_WON
    case GameResult.BlackWon             => ProtoGameResult.GAME_RESULT_BLACK_WON
    case GameResult.Stalemate            => ProtoGameResult.GAME_RESULT_STALEMATE
    case GameResult.FiftyMoveRule        => ProtoGameResult.GAME_RESULT_FIFTY_MOVE_RULE
    case GameResult.InsufficientMaterial => ProtoGameResult.GAME_RESULT_INSUFFICIENT_MATERIAL
    case GameResult.ThreefoldRepetition  => ProtoGameResult.GAME_RESULT_THREEFOLD_REPETITION
