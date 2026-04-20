package maichess.movevalidator.domain

sealed trait GameResult
object GameResult:
  case object None      extends GameResult
  case object WhiteWon  extends GameResult
  case object BlackWon  extends GameResult
  case object Stalemate extends GameResult
  case object Draw      extends GameResult
