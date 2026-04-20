package maichess.movevalidator.rules

// ── Color ─────────────────────────────────────────────────────────────────────
enum Color:
  case White, Black
  def opposite: Color = this match
    case White => Black
    case Black => White

// ── PieceType ─────────────────────────────────────────────────────────────────
enum PieceType:
  case King, Queen, Rook, Bishop, Knight, Pawn

// ── Piece ─────────────────────────────────────────────────────────────────────
case class Piece(color: Color, pieceType: PieceType)

// ── File (column a–h, stored as 0–7) ─────────────────────────────────────────
opaque type File = Int
object File:
  def fromChar(c: Char): Option[File] = Option.when(c >= 'a' && c <= 'h')(c - 'a')
  def fromInt(i: Int): Option[File]   = Option.when(i >= 0 && i <= 7)(i)
  val values: IndexedSeq[File]        = (0 to 7).toIndexedSeq
  extension (f: File)
    def toChar: Char = ('a' + f).toChar
    def toInt: Int   = f

// ── Rank (row 1–8, stored as 0–7) ────────────────────────────────────────────
opaque type Rank = Int
object Rank:
  def fromChar(c: Char): Option[Rank] = Option.when(c >= '1' && c <= '8')(c - '1')
  def fromInt(i: Int): Option[Rank]   = Option.when(i >= 0 && i <= 7)(i)
  val values: IndexedSeq[Rank]        = (0 to 7).toIndexedSeq
  extension (r: Rank)
    def toInt: Int   = r
    def toChar: Char = ('1' + r).toChar

// ── Square ────────────────────────────────────────────────────────────────────
case class Square(file: File, rank: Rank):
  def toAlgebraic: String = file.toChar.toString + rank.toChar.toString
  def offset(df: Int, dr: Int): Option[Square] =
    for f <- File.fromInt(file.toInt + df); r <- Rank.fromInt(rank.toInt + dr)
    yield Square(f, r)

object Square:
  def fromAlgebraic(s: String): Option[Square] =
    Option.when(s.length == 2)(
      for f <- File.fromChar(s(0)); r <- Rank.fromChar(s(1)) yield Square(f, r)
    ).flatten
  val all: IndexedSeq[Square] =
    for r <- Rank.values; f <- File.values yield Square(f, r)

// ── CastlingRights ────────────────────────────────────────────────────────────
case class CastlingRights(
  whiteKingSide:  Boolean,
  whiteQueenSide: Boolean,
  blackKingSide:  Boolean,
  blackQueenSide: Boolean,
)
object CastlingRights:
  val all:  CastlingRights = CastlingRights(true,  true,  true,  true)
  val none: CastlingRights = CastlingRights(false, false, false, false)

// ── Internal move representation ─────────────────────────────────────────────
sealed trait ChessMove:
  def from: Square
  def to:   Square

object ChessMove:
  case class Normal(from: Square, to: Square, promo: Option[PieceType]) extends ChessMove
  case class Castle(from: Square, to: Square, rookFrom: Square, rookTo: Square) extends ChessMove
  case class EnPassant(from: Square, to: Square, captured: Square) extends ChessMove
  def simple(from: Square, to: Square): Normal = Normal(from, to, None)

// ── Board: full position state ────────────────────────────────────────────────
case class Board(
  pieces:          Map[Square, Piece],
  sideToMove:      Color,
  castlingRights:  CastlingRights,
  enPassantSquare: Option[Square],
  halfMoveClock:   Int,
  fullMoveNumber:  Int,
):
  def pieceAt(sq: Square): Option[Piece] = pieces.get(sq)
