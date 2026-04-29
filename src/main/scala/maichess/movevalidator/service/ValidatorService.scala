package maichess.movevalidator.service

import zio.IO
import maichess.movevalidator.domain.{Fen, LegalMoveSan, UciMove, ValidateSanResult, ValidationResult}

trait ValidatorService:
  def validateMove(fen: Fen, move: UciMove, positionHistory: List[String]): IO[String, ValidationResult]
  def legalMoves(fen: Fen): IO[String, List[UciMove]]
  def validateMoveSan(fen: Fen, san: String, positionHistory: List[String]): IO[String, ValidateSanResult]
  def legalMovesSan(fen: Fen): IO[String, List[LegalMoveSan]]
  def convertSequenceToSan(startingFen: Fen, uciMoves: List[String]): IO[String, List[String]]
