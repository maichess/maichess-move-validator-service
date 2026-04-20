package maichess.movevalidator.rules

object FenParser:

  def parse(fen: String): Either[String, Board] =
    fen.split(' ') match
      case Array(board, side, castling, ep, half, full) =>
        for
          pieces  <- decodeBoard(board)
          color   <- decodeSide(side)
          cr      <- decodeCastling(castling)
          epSq    <- decodeEp(ep)
          halfInt <- toInt(half, "half-move clock")
          fullInt <- toInt(full, "full-move number")
        yield Board(pieces, color, cr, epSq, halfInt, fullInt)
      case _ => Left(s"FEN must have 6 space-separated fields, got: $fen")

  private def decodeBoard(s: String): Either[String, Map[Square, Piece]] =
    val ranks = s.split('/').toList
    if ranks.length != 8 then Left(s"Board must have 8 ranks, got ${ranks.length}")
    else
      ranks.zipWithIndex.foldLeft(Right(Map.empty[Square, Piece]): Either[String, Map[Square, Piece]]) {
        case (acc, (rankStr, fromTop)) =>
          acc.flatMap(m => decodeRank(rankStr, 7 - fromTop).map(m ++ _))
      }

  private def decodeRank(s: String, rankIdx: Int): Either[String, Map[Square, Piece]] =
    val (pairs, _, err) = s.foldLeft((Map.empty[Square, Piece], 0, Option.empty[String])) {
      case ((m, f, e), c) if c.isDigit => (m, f + c.asDigit, e)
      case ((m, f, e), c) =>
        charToPiece(c) match
          case None    => (m, f + 1, e.orElse(Some(s"Unknown piece char '$c'")))
          case Some(p) =>
            val optSq = for ff <- File.fromInt(f); r <- Rank.fromInt(rankIdx) yield Square(ff, r)
            optSq match
              case None      => (m, f + 1, e.orElse(Some(s"Invalid file $f")))
              case Some(dest) => (m.updated(dest, p), f + 1, e)
    }
    err.fold(Right(pairs): Either[String, Map[Square, Piece]])(Left(_))

  private def charToPiece(c: Char): Option[Piece] =
    val pt = c.toLower match
      case 'k' => Some(PieceType.King)
      case 'q' => Some(PieceType.Queen)
      case 'r' => Some(PieceType.Rook)
      case 'b' => Some(PieceType.Bishop)
      case 'n' => Some(PieceType.Knight)
      case 'p' => Some(PieceType.Pawn)
      case _   => None
    pt.map(Piece(if c.isUpper then Color.White else Color.Black, _))

  private def decodeSide(s: String): Either[String, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(s"Invalid active color '$s'")

  private def decodeCastling(s: String): Either[String, CastlingRights] =
    if s == "-" then Right(CastlingRights.none)
    else if s.forall("KQkq".contains) then
      Right(CastlingRights(s.contains('K'), s.contains('Q'), s.contains('k'), s.contains('q')))
    else Left(s"Invalid castling field '$s'")

  private def decodeEp(s: String): Either[String, Option[Square]] =
    if s == "-" then Right(None)
    else Square.fromAlgebraic(s) match
      case None     => Left(s"Invalid en passant square '$s'")
      case Some(sq) =>
        val r = sq.rank.toInt
        if r == 2 || r == 5 then Right(Some(sq))
        else Left(s"Invalid en passant square '$s'")

  private def toInt(s: String, name: String): Either[String, Int] =
    s.toIntOption.toRight(s"Invalid $name '$s'")
