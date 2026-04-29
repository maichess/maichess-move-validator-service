package maichess.movevalidator.domain

sealed trait ValidateSanResult
object ValidateSanResult:
  case class Valid(
    resultingFen:    Fen,
    gameResult:      GameResult,
    positionHistory: List[String],
    uciMove:         String,
  ) extends ValidateSanResult
  case class Invalid(reason: String) extends ValidateSanResult
