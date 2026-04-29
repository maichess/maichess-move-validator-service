package maichess.movevalidator.service

import zio.{IO, ULayer, ZIO, ZLayer}
import maichess.movevalidator.domain.{Fen, GameResult, LegalMoveSan, UciMove, ValidateSanResult, ValidationResult}
import maichess.movevalidator.rules.{
  FenParser, FenSerializer, LegalityFilter, MoveApplicator, MoveGenerator, SanNotation, WinConditionDetector,
}

final class ValidatorServiceLive extends ValidatorService:

  def validateMove(fen: Fen, move: UciMove, positionHistory: List[String]): IO[String, ValidationResult] =
    for
      board  <- ZIO.fromEither(FenParser.parse(fen.value))
      result <- ZIO.succeed(validate(board, move, positionHistory))
    yield result

  def legalMoves(fen: Fen): IO[String, List[UciMove]] =
    ZIO.fromEither(FenParser.parse(fen.value)).map(LegalityFilter.legalMoves)

  def validateMoveSan(fen: Fen, san: String, positionHistory: List[String]): IO[String, ValidateSanResult] =
    ZIO.fromEither(FenParser.parse(fen.value)).flatMap { board =>
      val legalChess = LegalityFilter.legalChessMoves(board)
      SanNotation.fromSan(board, san, legalChess) match
        case None        => ZIO.succeed(ValidateSanResult.Invalid(s"Unknown SAN move: $san"))
        case Some(chess) =>
          val uci = MoveGenerator.toUci(chess)
          validateMove(fen, uci, positionHistory).map(toSanResult(_, uci.value))
    }

  def legalMovesSan(fen: Fen): IO[String, List[LegalMoveSan]] =
    ZIO.fromEither(FenParser.parse(fen.value)).map { board =>
      val legalChess = LegalityFilter.legalChessMoves(board)
      legalChess.map(m => LegalMoveSan(MoveGenerator.toUci(m).value, SanNotation.toSan(board, m)))
    }

  def convertSequenceToSan(startingFen: Fen, uciMoves: List[String]): IO[String, List[String]] =
    ZIO.fromEither(FenParser.parse(startingFen.value)).flatMap { start =>
      ZIO.foldLeft(uciMoves.zipWithIndex)((start, List.empty[String])) {
        (accum, indexedMove) =>
          val (board, sans) = accum
          val (uciStr, idx) = indexedMove
          val legalChess    = LegalityFilter.legalChessMoves(board)
          legalChess.find(m => MoveGenerator.toUci(m).value == uciStr) match
            case None =>
              ZIO.fail(s"Move at index $idx is illegal: $uciStr")
            case Some(chess) =>
              val san  = SanNotation.toSan(board, chess)
              val next = MoveApplicator(board, chess)
              ZIO.succeed((next, sans :+ san))
      }.map { case (_, sanList) => sanList }
    }

  private def toSanResult(result: ValidationResult, uciMove: String): ValidateSanResult = result match
    case ValidationResult.Valid(fen, gr, hist) => ValidateSanResult.Valid(fen, gr, hist, uciMove)
    case ValidationResult.Invalid(reason)      => ValidateSanResult.Invalid(reason)

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
