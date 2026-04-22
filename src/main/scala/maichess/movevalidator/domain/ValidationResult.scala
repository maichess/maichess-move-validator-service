package maichess.movevalidator.domain

sealed trait ValidationResult
object ValidationResult:
  case class Valid(resultingFen: Fen, gameResult: GameResult, positionHistory: List[String]) extends ValidationResult
  case class Invalid(reason: String)                          extends ValidationResult
