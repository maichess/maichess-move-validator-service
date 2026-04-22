package maichess.movevalidator.service

import zio.{IO, ULayer, ZIO, ZLayer}
import maichess.movevalidator.domain.{Fen, GameResult, UciMove, ValidationResult}
import maichess.movevalidator.rules.{
  FenParser, FenSerializer, LegalityFilter, MoveApplicator, WinConditionDetector
}

final class ValidatorServiceLive extends ValidatorService:

  def validateMove(fen: Fen, move: UciMove, positionHistory: List[String]): IO[String, ValidationResult] =
    for
      board  <- ZIO.fromEither(FenParser.parse(fen.value))
      result <- ZIO.succeed(validate(board, move, positionHistory))
    yield result

  def legalMoves(fen: Fen): IO[String, List[UciMove]] =
    ZIO.fromEither(FenParser.parse(fen.value)).map(LegalityFilter.legalMoves)

  private def validate(board: maichess.movevalidator.rules.Board, move: UciMove, positionHistory: List[String]): ValidationResult =
    if !LegalityFilter.isLegal(board, move) then
      ValidationResult.Invalid(s"Illegal move: ${move.value}")
    else
      MoveApplicator.fromUci(board, move.value) match
        case None        => ValidationResult.Invalid(s"Cannot parse move: ${move.value}")
        case Some(chess) =>
          val next         = MoveApplicator(board, chess)
          val nextFen      = Fen(FenSerializer.serialize(next))
          val posKey       = positionKey(nextFen.value)
          val irreversible = next.halfMoveClock == 0
          val updated      = if irreversible then List(posKey) else positionHistory :+ posKey
          val gameResult   =
            if !irreversible && positionHistory.count(_ == posKey) >= 2 then GameResult.ThreefoldRepetition
            else WinConditionDetector.detect(next)
          ValidationResult.Valid(nextFen, gameResult, updated)

  private def positionKey(fen: String): String =
    fen.split(' ').take(4).mkString(" ")

object ValidatorServiceLive:
  val layer: ULayer[ValidatorService] = ZLayer.succeed(new ValidatorServiceLive)
