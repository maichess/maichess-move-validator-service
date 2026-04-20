package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.domain.Fen
import maichess.movevalidator.rules.{Board, Color, FenParser, FenSerializer, PieceType}

object FenParserSpec extends ZIOSpecDefault:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def spec = suite("FenParserSpec")(
    test("parses starting position side to move") {
      val result = FenParser.parse(startFen)
      assertTrue(result.map(_.sideToMove) == Right(Color.White))
    },
    test("parses starting position castling rights") {
      val result = FenParser.parse(startFen)
      assertTrue(result.exists { b =>
        b.castlingRights.whiteKingSide &&
        b.castlingRights.whiteQueenSide &&
        b.castlingRights.blackKingSide &&
        b.castlingRights.blackQueenSide
      })
    },
    test("parses starting position piece count") {
      val result = FenParser.parse(startFen)
      assertTrue(result.exists(_.pieces.size == 32))
    },
    test("parses starting position white pawns") {
      val result = FenParser.parse(startFen)
      assertTrue(result.exists { b =>
        b.pieces.values.count(p => p.color == Color.White && p.pieceType == PieceType.Pawn) == 8
      })
    },
    test("rejects FEN with wrong field count") {
      assertTrue(FenParser.parse("rnbqkbnr w KQkq - 0").isLeft)
    },
    test("rejects FEN with invalid board structure") {
      assertTrue(FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1").isLeft)
    },
    test("rejects FEN with invalid side to move") {
      assertTrue(FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1").isLeft)
    },
    test("rejects FEN with invalid castling field") {
      assertTrue(FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w XY - 0 1").isLeft)
    },
    test("rejects FEN with invalid en passant square") {
      assertTrue(FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e4 0 1").isLeft)
    },
    test("parses FEN with en passant square") {
      val fen    = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val result = FenParser.parse(fen)
      assertTrue(result.exists(_.enPassantSquare.isDefined))
    },
    test("round-trips starting FEN through FenSerializer") {
      val result = FenParser.parse(startFen).map(FenSerializer.serialize)
      assertTrue(result == Right(startFen))
    },
    test("round-trips mid-game FEN") {
      val fen    = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
      val result = FenParser.parse(fen).map(FenSerializer.serialize)
      assertTrue(result == Right(fen))
    },
    test("Fen.parse rejects FEN with wrong field count") {
      assertTrue(Fen.parse("too short").isLeft)
    },
    test("Fen.parse accepts valid starting FEN") {
      assertTrue(Fen.parse(startFen).isRight)
    },
  )
