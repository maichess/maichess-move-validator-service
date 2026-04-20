package maichess.movevalidator.rules

object FenSerializer:

  def serialize(board: Board): String =
    val parts = List(
      encodeBoard(board.pieces),
      if board.sideToMove == Color.White then "w" else "b",
      encodeCastling(board.castlingRights),
      board.enPassantSquare.fold("-")(_.toAlgebraic),
      board.halfMoveClock.toString,
      board.fullMoveNumber.toString,
    )
    parts.mkString(" ")

  private def encodeBoard(pieces: Map[Square, Piece]): String =
    (7 to 0 by -1).map { ri =>
      val row = (0 to 7).toList.map { fi =>
        for f <- File.fromInt(fi); r <- Rank.fromInt(ri) yield Square(f, r)
      }
      encodeRow(row.map(_.flatMap(pieces.get)))
    }.mkString("/")

  private def encodeRow(cells: List[Option[Piece]]): String =
    val (result, empties) = cells.foldLeft(("", 0)) {
      case ((acc, n), None)        => (acc, n + 1)
      case ((acc, n), Some(piece)) =>
        val prefix = if n > 0 then n.toString else ""
        (acc + prefix + pieceChar(piece).toString, 0)
    }
    if empties > 0 then result + empties.toString else result

  private def pieceChar(piece: Piece): Char =
    val c = piece.pieceType match
      case PieceType.King   => 'k'
      case PieceType.Queen  => 'q'
      case PieceType.Rook   => 'r'
      case PieceType.Bishop => 'b'
      case PieceType.Knight => 'n'
      case PieceType.Pawn   => 'p'
    if piece.color == Color.White then c.toUpper else c

  private def encodeCastling(cr: CastlingRights): String =
    val s = (if cr.whiteKingSide  then "K" else "") +
            (if cr.whiteQueenSide then "Q" else "") +
            (if cr.blackKingSide  then "k" else "") +
            (if cr.blackQueenSide then "q" else "")
    if s.isEmpty then "-" else s
