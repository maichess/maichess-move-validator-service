package maichess.movevalidator.rules

import maichess.movevalidator.domain.UciMove

object LegalityFilter:
  private val rookDirs:   List[(Int, Int)] = List((1,0),(-1,0),(0,1),(0,-1))
  private val bishopDirs: List[(Int, Int)] = List((1,1),(1,-1),(-1,1),(-1,-1))
  private val queenDirs:  List[(Int, Int)] = rookDirs ++ bishopDirs
  private val knightJumps: List[(Int, Int)] =
    List((2,1),(2,-1),(-2,1),(-2,-1),(1,2),(1,-2),(-1,2),(-1,-2))

  def legalMoves(board: Board): List[UciMove] =
    MoveGenerator.pseudoLegal(board)
      .filter(m => !leavesKingInCheck(board, m) && !castlesThroughCheck(board, m))
      .map(MoveGenerator.toUci)

  def legalChessMoves(board: Board): List[ChessMove] =
    MoveGenerator.pseudoLegal(board)
      .filter(m => !leavesKingInCheck(board, m) && !castlesThroughCheck(board, m))

  def isLegal(board: Board, move: UciMove): Boolean =
    legalMoves(board).contains(move)

  def isCheck(board: Board): Boolean =
    kingSquare(board.pieces, board.sideToMove)
      .fold(false)(sq => isAttackedBy(board.pieces, sq, board.sideToMove.opposite))

  private def leavesKingInCheck(board: Board, move: ChessMove): Boolean =
    val next = MoveApplicator(board, move)
    kingSquare(next.pieces, board.sideToMove)
      .fold(false)(sq => isAttackedBy(next.pieces, sq, board.sideToMove.opposite))

  private def castlesThroughCheck(board: Board, move: ChessMove): Boolean = move match
    case ChessMove.Castle(from, to, _, _) =>
      val passSq = if to.file.toInt > from.file.toInt then from.offset(1, 0) else from.offset(-1, 0)
      isCheck(board) || passSq.fold(false)(sq => isAttackedBy(board.pieces, sq, board.sideToMove.opposite))
    case _ => false

  private def kingSquare(pieces: Map[Square, Piece], color: Color): Option[Square] =
    Square.all.find(sq => pieces.get(sq).contains(Piece(color, PieceType.King)))

  private def isAttackedBy(pieces: Map[Square, Piece], target: Square, attacker: Color): Boolean =
    Square.all.exists { sq =>
      pieces.get(sq).fold(false)(p => p.color == attacker && attacks(pieces, sq, p, target))
    }

  private def attacks(pieces: Map[Square, Piece], from: Square, piece: Piece, target: Square): Boolean =
    val fwd = if piece.color == Color.White then 1 else -1
    piece.pieceType match
      case PieceType.Pawn   =>
        from.offset(-1, fwd).contains(target) || from.offset(1, fwd).contains(target)
      case PieceType.Knight =>
        knightJumps.exists { (df, dr) => from.offset(df, dr).contains(target) }
      case PieceType.Bishop => rayReaches(pieces, from, bishopDirs, target)
      case PieceType.Rook   => rayReaches(pieces, from, rookDirs,   target)
      case PieceType.Queen  => rayReaches(pieces, from, queenDirs,  target)
      case PieceType.King   =>
        queenDirs.exists { (df, dr) => from.offset(df, dr).contains(target) }

  private def rayReaches(pieces: Map[Square, Piece], from: Square, dirs: List[(Int, Int)], target: Square): Boolean =
    dirs.exists { dir =>
      @annotation.tailrec
      def loop(sq: Square): Boolean = sq.offset(dir._1, dir._2) match
        case None                                      => false
        case Some(next) if next == target              => true
        case Some(next) if pieces.get(next).isEmpty    => loop(next)
        case Some(_)                                   => false
      loop(from)
    }
