package maichess.movevalidator.service

import zio.IO
import maichess.movevalidator.domain.{Fen, UciMove, ValidationResult}

trait ValidatorService:
  def validateMove(fen: Fen, move: UciMove, positionHistory: List[String]): IO[String, ValidationResult]
  def legalMoves(fen: Fen): IO[String, List[UciMove]]
