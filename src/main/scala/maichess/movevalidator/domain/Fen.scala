package maichess.movevalidator.domain

case class Fen(value: String)

object Fen:
  def parse(raw: String): Either[String, Fen] =
    raw.split(' ') match
      case Array(board, side, castling, ep, half, full) =>
        for
          _ <- validateBoard(board)
          _ <- validateSide(side)
          _ <- validateCastling(castling)
          _ <- validateEp(ep)
          _ <- validateNonNeg(half, "half-move clock")
          _ <- validatePositive(full, "full-move number")
        yield Fen(raw)
      case _ => Left(s"FEN must have 6 space-separated fields, got: $raw")

  private def validateBoard(s: String): Either[String, Unit] =
    val ranks = s.split('/')
    if ranks.length != 8 then Left(s"Board must have 8 ranks, got ${ranks.length}")
    else
      val errors = ranks.zipWithIndex.flatMap { case (rank, i) => rankErrors(rank, i) }.toList
      errors match
        case Nil    => Right(())
        case e :: _ => Left(e)

  private def rankErrors(s: String, idx: Int): List[String] =
    val (total, errs) = s.foldLeft((0, List.empty[String])) {
      case ((n, es), c) if c.isDigit && c != '0' => (n + c.asDigit, es)
      case ((n, es), c) if "KQRBNPkqrbnp".contains(c) => (n + 1, es)
      case ((n, es), c) => (n, es :+ s"Unknown piece char '$c' in rank $idx")
    }
    val countErr = if total != 8 then List(s"Rank $idx has $total files, expected 8") else Nil
    errs ++ countErr

  private def validateSide(s: String): Either[String, Unit] =
    if s == "w" || s == "b" then Right(())
    else Left(s"Active color must be 'w' or 'b', got '$s'")

  private def validateCastling(s: String): Either[String, Unit] =
    if s == "-" || s.nonEmpty && s.forall("KQkq".contains) then Right(())
    else Left(s"Invalid castling field: '$s'")

  private def validateEp(s: String): Either[String, Unit] =
    if s == "-" then Right(())
    else if s.length == 2 && s(0) >= 'a' && s(0) <= 'h' && (s(1) == '3' || s(1) == '6') then Right(())
    else Left(s"Invalid en passant square: '$s'")

  private def validateNonNeg(s: String, name: String): Either[String, Unit] =
    s.toIntOption match
      case Some(n) if n >= 0 => Right(())
      case _ => Left(s"Invalid $name: '$s'")

  private def validatePositive(s: String, name: String): Either[String, Unit] =
    s.toIntOption match
      case Some(n) if n >= 1 => Right(())
      case _ => Left(s"Invalid $name: '$s'")
