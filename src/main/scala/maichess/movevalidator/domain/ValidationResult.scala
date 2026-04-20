package maichess.movevalidator.domain

sealed trait ValidationResult
object ValidationResult:
  case class Valid(resultingFen: Fen, gameResult: GameResult) extends ValidationResult
  case class Invalid(reason: String)                          extends ValidationResult
