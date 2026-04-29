package maichess.movevalidator.rules

object SanNotation:

  def toSan(board: Board, move: ChessMove): String =
    move match
      case ChessMove.Castle(_, to, _, _) =>
        if to.file.toInt > 4 then "O-O" else "O-O-O"
      case _ =>
        board.pieceAt(move.from) match
          case None        => ""
          case Some(piece) =>
            if piece.pieceType == PieceType.Pawn then pawnSan(board, move)
            else pieceSan(board, piece.pieceType, move)

  def fromSan(board: Board, san: String, legalMoves: List[ChessMove]): Option[ChessMove] =
    val normalized = san.stripSuffix("#").stripSuffix("+")
    legalMoves.find(m => toSan(board, m).stripSuffix("#").stripSuffix("+") == normalized)

  private def pawnSan(board: Board, move: ChessMove): String =
    val cap    = isCapture(board, move)
    val prefix = if cap then s"${move.from.file.toChar}x" else ""
    val next   = MoveApplicator(board, move)
    s"$prefix${move.to.toAlgebraic}${promoSuffix(move)}${checkSuffix(next)}"

  private def pieceSan(board: Board, pt: PieceType, move: ChessMove): String =
    val legalMoves = LegalityFilter.legalChessMoves(board)
    val cap        = if isCapture(board, move) then "x" else ""
    val next       = MoveApplicator(board, move)
    s"${pieceChar(pt)}${disambig(board, move, pt, legalMoves)}$cap${move.to.toAlgebraic}${checkSuffix(next)}"

  private def disambig(board: Board, move: ChessMove, pt: PieceType, legalMoves: List[ChessMove]): String =
    val ambiguous = legalMoves.filter { m =>
      m != move && m.to == move.to && board.pieceAt(m.from).exists(_.pieceType == pt)
    }
    if ambiguous.isEmpty then ""
    else if ambiguous.forall(_.from.file != move.from.file) then move.from.file.toChar.toString
    else if ambiguous.forall(_.from.rank != move.from.rank) then move.from.rank.toChar.toString
    else move.from.toAlgebraic

  private def isCapture(board: Board, move: ChessMove): Boolean = move match
    case _: ChessMove.EnPassant => true
    case _                      => board.pieceAt(move.to).isDefined

  private def promoSuffix(move: ChessMove): String = move match
    case ChessMove.Normal(_, _, Some(pt)) => s"=${promoChar(pt)}"
    case _                                => ""

  private def checkSuffix(nextBoard: Board): String =
    if !LegalityFilter.isCheck(nextBoard) then ""
    else if LegalityFilter.legalChessMoves(nextBoard).nonEmpty then "+"
    else "#"

  // private[movevalidator] so SanNotationSpec can reach the Pawn branch for 100% coverage
  private[movevalidator] def pieceChar(pt: PieceType): String = pt match
    case PieceType.Knight => "N"
    case PieceType.Bishop => "B"
    case PieceType.Rook   => "R"
    case PieceType.Queen  => "Q"
    case PieceType.King   => "K"
    case PieceType.Pawn   => ""

  // private[movevalidator] so SanNotationSpec can reach the unreachable promotion fallback
  private[movevalidator] def promoChar(pt: PieceType): String = pt match
    case PieceType.Queen  => "Q"
    case PieceType.Rook   => "R"
    case PieceType.Bishop => "B"
    case PieceType.Knight => "N"
    case _                => ""
