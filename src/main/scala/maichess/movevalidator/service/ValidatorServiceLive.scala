package maichess.movevalidator.service

import zio.{IO, ULayer, ZIO, ZLayer}
import maichess.movevalidator.domain.{Fen, UciMove, ValidationResult}
import maichess.movevalidator.rules.{
  FenParser, FenSerializer, LegalityFilter, MoveApplicator, WinConditionDetector
}

final class ValidatorServiceLive extends ValidatorService:

  def validateMove(fen: Fen, move: UciMove): IO[String, ValidationResult] =
    for
      board  <- ZIO.fromEither(FenParser.parse(fen.value))
      result <- ZIO.succeed(validate(board, move))
    yield result

  def legalMoves(fen: Fen): IO[String, List[UciMove]] =
    ZIO.fromEither(FenParser.parse(fen.value)).map(LegalityFilter.legalMoves)

  private def validate(board: maichess.movevalidator.rules.Board, move: UciMove): ValidationResult =
    if !LegalityFilter.isLegal(board, move) then
      ValidationResult.Invalid(s"Illegal move: ${move.value}")
    else
      MoveApplicator.fromUci(board, move.value) match
        case None        => ValidationResult.Invalid(s"Cannot parse move: ${move.value}")
        case Some(chess) =>
          val next       = MoveApplicator(board, chess)
          val nextFen    = Fen(FenSerializer.serialize(next))
          val gameResult = WinConditionDetector.detect(next)
          ValidationResult.Valid(nextFen, gameResult)

object ValidatorServiceLive:
  val layer: ULayer[ValidatorService] = ZLayer.succeed(new ValidatorServiceLive)
