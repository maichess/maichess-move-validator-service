package maichess.movevalidator.rules

import maichess.movevalidator.domain.GameResult

object WinConditionDetector:

  // Board is the position AFTER the move; sideToMove has already been flipped.
  def detect(board: Board): GameResult =
    val noMoves = LegalityFilter.legalChessMoves(board).isEmpty
    val inCheck  = LegalityFilter.isCheck(board)
    if noMoves && inCheck then
      if board.sideToMove == Color.White then GameResult.BlackWon else GameResult.WhiteWon
    else if noMoves then GameResult.Stalemate
    else if isFiftyMoveRule(board) then GameResult.FiftyMoveRule
    else if isInsufficientMaterial(board) then GameResult.InsufficientMaterial
    else GameResult.None

  private def isFiftyMoveRule(board: Board): Boolean =
    board.halfMoveClock >= 100

  private def isInsufficientMaterial(board: Board): Boolean =
    val nonKings = board.pieces.values.iterator.toList.filter(_.pieceType != PieceType.King)
    nonKings match
      case Nil => true
      case List(p) if p.pieceType == PieceType.Bishop || p.pieceType == PieceType.Knight => true
      case List(p1, p2)
        if p1.pieceType == PieceType.Bishop && p2.pieceType == PieceType.Bishop
           && p1.color != p2.color
           && sameBishopColor(board, p1.color, p2.color) => true
      case _ => false

  private def sameBishopColor(board: Board, c1: Color, c2: Color): Boolean =
    val b1 = bishopSquareColor(board, c1)
    val b2 = bishopSquareColor(board, c2)
    b1.isDefined && b2.isDefined && b1 == b2

  private def bishopSquareColor(board: Board, color: Color): Option[Boolean] =
    board.pieces.collectFirst {
      case (sq, Piece(c, PieceType.Bishop)) if c == color =>
        (sq.file.toInt + sq.rank.toInt) % 2 == 0
    }
