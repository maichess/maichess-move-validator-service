package maichess.movevalidator.domain

case class UciMove(value: String)

object UciMove:
  private val promotionPieces = Set('q', 'r', 'b', 'n')

  def parse(raw: String): Either[String, UciMove] =
    raw.length match
      case 4 => validateSquares(raw, raw)
      case 5 => validateSquares(raw, raw).flatMap(_ => validatePromotion(raw))
      case _ => Left(s"UCI move must be 4 or 5 characters, got ${raw.length}: '$raw'")

  private def validateSquares(raw: String, _orig: String): Either[String, UciMove] =
    if isValidSquare(raw.substring(0, 2)) && isValidSquare(raw.substring(2, 4)) then Right(UciMove(raw))
    else Left(s"Invalid squares in UCI move: '$raw'")

  private def validatePromotion(raw: String): Either[String, UciMove] =
    if promotionPieces.contains(raw(4)) then Right(UciMove(raw))
    else Left(s"Invalid promotion piece '${raw(4)}' in UCI move: '$raw'")

  private def isValidSquare(s: String): Boolean =
    s.length == 2 && s(0) >= 'a' && s(0) <= 'h' && s(1) >= '1' && s(1) <= '8'
