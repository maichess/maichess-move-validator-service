package maichess.movevalidator.rules

object MoveApplicator:

  def apply(board: Board, move: ChessMove): Board =
    Board(
      pieces          = applyPieces(board.pieces, move),
      sideToMove      = board.sideToMove.opposite,
      castlingRights  = updatedCastling(board, move),
      enPassantSquare = enPassantAfter(board, move),
      halfMoveClock   = halfClockAfter(board, move),
      fullMoveNumber  =
        if board.sideToMove == Color.Black then board.fullMoveNumber + 1
        else board.fullMoveNumber,
    )

  private def applyPieces(pieces: Map[Square, Piece], move: ChessMove): Map[Square, Piece] =
    move match
      case ChessMove.Normal(from, to, promo) =>
        pieces.get(from) match
          case None    => pieces.removed(from)
          case Some(p) => pieces.removed(from).updated(to, promo.fold(p)(pt => p.copy(pieceType = pt)))
      case ChessMove.Castle(from, to, rf, rt) =>
        val withoutSrc = pieces.removed(from).removed(rf)
        val withKing   = pieces.get(from).fold(withoutSrc)(k => withoutSrc.updated(to, k))
        pieces.get(rf).fold(withKing)(r => withKing.updated(rt, r))
      case ChessMove.EnPassant(from, to, cap) =>
        val without = pieces.removed(from).removed(cap)
        pieces.get(from).fold(without)(p => without.updated(to, p))

  private def updatedCastling(board: Board, move: ChessMove): CastlingRights =
    board.pieceAt(move.from) match
      case Some(Piece(Color.White, PieceType.King)) =>
        board.castlingRights.copy(whiteKingSide = false, whiteQueenSide = false)
      case Some(Piece(Color.Black, PieceType.King)) =>
        board.castlingRights.copy(blackKingSide = false, blackQueenSide = false)
      case Some(Piece(Color.White, PieceType.Rook)) => move.from.toAlgebraic match
        case "a1" => board.castlingRights.copy(whiteQueenSide = false)
        case "h1" => board.castlingRights.copy(whiteKingSide  = false)
        case _    => board.castlingRights
      case Some(Piece(Color.Black, PieceType.Rook)) => move.from.toAlgebraic match
        case "a8" => board.castlingRights.copy(blackQueenSide = false)
        case "h8" => board.castlingRights.copy(blackKingSide  = false)
        case _    => board.castlingRights
      case _ => board.castlingRights

  private def enPassantAfter(board: Board, move: ChessMove): Option[Square] = move match
    case ChessMove.Normal(from, to, None) =>
      val diff   = to.rank.toInt - from.rank.toInt
      val double = board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
                   (diff == 2 || diff == -2)
      Option.when(double)(from.offset(0, diff / 2)).flatten
    case _ => None

  private def halfClockAfter(board: Board, move: ChessMove): Int =
    val isPawn    = board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn)
    val isCapture = board.pieceAt(move.to).isDefined || isEnPassant(move)
    if isPawn || isCapture then 0 else board.halfMoveClock + 1

  private def isEnPassant(move: ChessMove): Boolean = move match
    case _: ChessMove.EnPassant => true
    case _                      => false

  def fromUci(board: Board, uci: String): Option[ChessMove] =
    if uci.length != 4 && uci.length != 5 then None
    else
      for
        from <- Square.fromAlgebraic(uci.substring(0, 2))
        to   <- Square.fromAlgebraic(uci.substring(2, 4))
      yield resolveMove(board, from, to, Option.when(uci.length == 5)(uci(4)))

  private def resolveMove(board: Board, from: Square, to: Square, promoChar: Option[Char]): ChessMove =
    val isKing   = board.pieceAt(from).exists(_.pieceType == PieceType.King)
    val isPawn   = board.pieceAt(from).exists(_.pieceType == PieceType.Pawn)
    val isEp     = isPawn && board.enPassantSquare.contains(to)
    val isCastle = isKing && isCastlingTarget(from, to)
    if isCastle then castleMove(from, to)
    else if isEp then ChessMove.EnPassant(from, to, Square(to.file, from.rank))
    else ChessMove.Normal(from, to, promoChar.flatMap(charToPromo))

  private def isCastlingTarget(from: Square, to: Square): Boolean =
    (from.toAlgebraic, to.toAlgebraic) match
      case ("e1","g1") | ("e1","c1") | ("e8","g8") | ("e8","c8") => true
      case _ => false

  private def castleMove(from: Square, to: Square): ChessMove =
    val kingSide     = to.file.toInt > from.file.toInt
    val rookFromFile = if kingSide then File.fromInt(7) else File.fromInt(0)
    val rookToFile   = if kingSide then File.fromInt(5) else File.fromInt(3)
    (rookFromFile, rookToFile) match
      case (Some(rf), Some(rt)) =>
        ChessMove.Castle(from, to, Square(rf, from.rank), Square(rt, from.rank))
      case _ => ChessMove.simple(from, to)

  private def charToPromo(c: Char): Option[PieceType] = c match
    case 'q' => Some(PieceType.Queen)
    case 'r' => Some(PieceType.Rook)
    case 'b' => Some(PieceType.Bishop)
    case 'n' => Some(PieceType.Knight)
    case _   => None
