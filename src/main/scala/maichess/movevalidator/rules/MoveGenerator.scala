package maichess.movevalidator.rules

import maichess.movevalidator.domain.UciMove

object MoveGenerator:
  private val rookDirs:    List[(Int, Int)] = List((1,0),(-1,0),(0,1),(0,-1))
  private val bishopDirs:  List[(Int, Int)] = List((1,1),(1,-1),(-1,1),(-1,-1))
  private val queenDirs:   List[(Int, Int)] = rookDirs ++ bishopDirs
  private val knightJumps: List[(Int, Int)] =
    List((2,1),(2,-1),(-2,1),(-2,-1),(1,2),(1,-2),(-1,2),(-1,-2))

  def pseudoLegal(board: Board): List[ChessMove] =
    Square.all.toList.flatMap(movesFrom(board, _))

  private def movesFrom(board: Board, sq: Square): List[ChessMove] =
    board.pieceAt(sq).fold(List.empty[ChessMove]) { p =>
      if p.color != board.sideToMove then List.empty[ChessMove]
      else p.pieceType match
        case PieceType.Pawn   => pawnMoves(board, sq, p.color)
        case PieceType.Knight => knightMoves(board, sq, p.color)
        case PieceType.Bishop => slidingMoves(board, sq, p.color, bishopDirs)
        case PieceType.Rook   => slidingMoves(board, sq, p.color, rookDirs)
        case PieceType.Queen  => slidingMoves(board, sq, p.color, queenDirs)
        case PieceType.King   => kingMoves(board, sq, p.color)
    }

  def toUci(move: ChessMove): UciMove = move match
    case ChessMove.Normal(f, t, None)    => UciMove(f.toAlgebraic + t.toAlgebraic)
    case ChessMove.Normal(f, t, Some(p)) => UciMove(f.toAlgebraic + t.toAlgebraic + promoChar(p))
    case ChessMove.Castle(f, t, _, _)    => UciMove(f.toAlgebraic + t.toAlgebraic)
    case ChessMove.EnPassant(f, t, _)    => UciMove(f.toAlgebraic + t.toAlgebraic)

  private def promoChar(pt: PieceType): String = pt match
    case PieceType.Queen  => "q"
    case PieceType.Rook   => "r"
    case PieceType.Bishop => "b"
    case PieceType.Knight => "n"
    case PieceType.King   => "k"
    case PieceType.Pawn   => "p"

  private def slidingMoves(board: Board, from: Square, color: Color, dirs: List[(Int, Int)]): List[ChessMove] =
    dirs.flatMap(castRay(board, from, color, _))

  private def castRay(board: Board, from: Square, color: Color, dir: (Int, Int)): List[ChessMove] =
    @annotation.tailrec
    def loop(sq: Square, acc: List[ChessMove]): List[ChessMove] =
      sq.offset(dir._1, dir._2) match
        case None       => acc
        case Some(next) => board.pieceAt(next) match
          case None                        => loop(next, ChessMove.simple(from, next) :: acc)
          case Some(p) if p.color != color => ChessMove.simple(from, next) :: acc
          case Some(_)                     => acc
    loop(from, Nil).reverse

  private def knightMoves(board: Board, from: Square, color: Color): List[ChessMove] =
    knightJumps.flatMap { (df, dr) =>
      from.offset(df, dr).flatMap { to =>
        board.pieceAt(to) match
          case Some(p) if p.color == color => None
          case _ => Some(ChessMove.simple(from, to))
      }
    }

  private def kingMoves(board: Board, from: Square, color: Color): List[ChessMove] =
    val steps = queenDirs.flatMap { (df, dr) =>
      from.offset(df, dr).flatMap { to =>
        board.pieceAt(to) match
          case Some(p) if p.color == color => None
          case _ => Some(ChessMove.simple(from, to))
      }
    }
    steps ++ castlingMoves(board, from, color)

  private def castlingMoves(board: Board, from: Square, color: Color): List[ChessMove] =
    color match
      case Color.White => whiteCastles(board, from)
      case Color.Black => blackCastles(board, from)

  private def whiteCastles(board: Board, from: Square): List[ChessMove] =
    castlePair(board, from, "e1",
      board.castlingRights.whiteKingSide,  List("f1","g1"), ("e1","g1","h1","f1"),
      board.castlingRights.whiteQueenSide, List("b1","c1","d1"), ("e1","c1","a1","d1"))

  private def blackCastles(board: Board, from: Square): List[ChessMove] =
    castlePair(board, from, "e8",
      board.castlingRights.blackKingSide,  List("f8","g8"), ("e8","g8","h8","f8"),
      board.castlingRights.blackQueenSide, List("b8","c8","d8"), ("e8","c8","a8","d8"))

  private def castlePair(
    board: Board, from: Square, kingAlg: String,
    ksr: Boolean, ksClr: List[String], ksCoords: (String,String,String,String),
    qsr: Boolean, qsClr: List[String], qsCoords: (String,String,String,String),
  ): List[ChessMove] =
    val expected = Square.fromAlgebraic(kingAlg)
    List(
      Option.when(ksr && expected.contains(from) && allEmpty(board, ksClr))(makeCastle(ksCoords)),
      Option.when(qsr && expected.contains(from) && allEmpty(board, qsClr))(makeCastle(qsCoords)),
    ).flatten.flatten

  private def allEmpty(board: Board, algs: List[String]): Boolean =
    algs.forall(a => Square.fromAlgebraic(a).fold(false)(board.pieceAt(_).isEmpty))

  private def makeCastle(coords: (String,String,String,String)): Option[ChessMove] =
    for
      kf <- Square.fromAlgebraic(coords._1)
      kt <- Square.fromAlgebraic(coords._2)
      rf <- Square.fromAlgebraic(coords._3)
      rt <- Square.fromAlgebraic(coords._4)
    yield ChessMove.Castle(kf, kt, rf, rt)

  private def pawnMoves(board: Board, from: Square, color: Color): List[ChessMove] =
    val fwd  = if color == Color.White then 1  else -1
    val sRnk = if color == Color.White then 1  else 6
    val pRnk = if color == Color.White then 7  else 0

    val single = from.offset(0, fwd).filter(to => board.pieceAt(to).isEmpty)
    val double = Option.when(from.rank.toInt == sRnk) {
      from.offset(0, fwd).flatMap(mid =>
        Option.when(board.pieceAt(mid).isEmpty)(
          from.offset(0, fwd * 2).filter(to => board.pieceAt(to).isEmpty)
        ).flatten
      )
    }.flatten
    val captures = List(-1, 1).flatMap(df =>
      from.offset(df, fwd).flatMap(to =>
        board.pieceAt(to).filter(_.color != color).map(_ => to)
      )
    )
    val ep = board.enPassantSquare.toList.flatMap(epSq =>
      List(-1, 1).flatMap(df =>
        from.offset(df, fwd).filter(_ == epSq).map(to =>
          ChessMove.EnPassant(from, to, Square(to.file, from.rank))
        )
      )
    )

    def toMoves(dest: Square): List[ChessMove] =
      if dest.rank.toInt == pRnk then
        List(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight)
          .map(pt => ChessMove.Normal(from, dest, Some(pt)))
      else List(ChessMove.simple(from, dest))

    (single.toList ++ double.toList).flatMap(toMoves) ++
      captures.flatMap(toMoves) ++ ep
