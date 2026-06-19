package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{Board, FenParser, MoveApplicator, PieceType}

// Targets the MoveApplicator rule mutants the position-only MoveApplicatorSpec leaves
// alive: per-square castling-rights clearing, the en-passant target square, and the
// half-move clock on a non-pawn capture.
object MoveApplicatorRulesSpec extends ZIOSpecDefault:

  private def applyMove(fen: String, uci: String): Board =
    FenParser.parse(fen).fold(
      e => throw new RuntimeException(e),
      b => MoveApplicator.fromUci(b, uci) match
        case None    => throw new RuntimeException(s"cannot resolve $uci")
        case Some(m) => MoveApplicator(b, m),
    )

  private val whiteRooks = "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"
  private val blackRooks = "r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1"

  def spec = suite("MoveApplicatorRulesSpec")(

    // ── castling rights cleared per moved piece ──────────────────────────────
    test("white king move clears both white castling rights") {
      val cr = applyMove(whiteRooks, "e1e2").castlingRights
      assertTrue(!cr.whiteKingSide, !cr.whiteQueenSide)
    },
    test("white a1-rook move clears only white queenside") {
      val cr = applyMove(whiteRooks, "a1a2").castlingRights
      assertTrue(cr.whiteKingSide, !cr.whiteQueenSide)
    },
    test("white h1-rook move clears only white kingside") {
      val cr = applyMove(whiteRooks, "h1h2").castlingRights
      assertTrue(!cr.whiteKingSide, cr.whiteQueenSide)
    },
    test("black king move clears both black castling rights") {
      val cr = applyMove(blackRooks, "e8e7").castlingRights
      assertTrue(!cr.blackKingSide, !cr.blackQueenSide)
    },
    test("black a8-rook move clears only black queenside") {
      val cr = applyMove(blackRooks, "a8a7").castlingRights
      assertTrue(cr.blackKingSide, !cr.blackQueenSide)
    },
    test("black h8-rook move clears only black kingside") {
      val cr = applyMove(blackRooks, "h8h7").castlingRights
      assertTrue(!cr.blackKingSide, cr.blackQueenSide)
    },
    test("a non-king non-rook move leaves castling rights untouched") {
      // The white king's knight is not present; move the e1 king's... use a pawn push.
      val cr = applyMove("4k3/8/8/8/8/8/4P3/R3K2R w KQ - 0 1", "e2e3").castlingRights
      assertTrue(cr.whiteKingSide, cr.whiteQueenSide)
    },

    // ── en-passant target square ─────────────────────────────────────────────
    test("white double pawn push sets the e3 en-passant target") {
      val b = applyMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "e2e4")
      assertTrue(b.enPassantSquare.map(_.toAlgebraic) == Some("e3"))
    },
    test("black double pawn push sets the d6 en-passant target") {
      val b = applyMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1", "d7d5")
      assertTrue(b.enPassantSquare.map(_.toAlgebraic) == Some("d6"))
    },
    test("single pawn push sets no en-passant target") {
      val b = applyMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "e2e3")
      assertTrue(b.enPassantSquare.isEmpty)
    },
    test("a non-pawn move sets no en-passant target") {
      val b = applyMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "g1f3")
      assertTrue(b.enPassantSquare.isEmpty)
    },

    // ── half-move clock on a non-pawn capture ────────────────────────────────
    test("a non-pawn capture resets the half-move clock to zero") {
      // White knight on e3 captures the black pawn on d5; isCapture must hold even
      // though the mover is not a pawn.
      val b = applyMove("4k3/8/8/3p4/8/4N3/8/4K3 w - - 5 10", "e3d5")
      assertTrue(b.halfMoveClock == 0)
    },
    test("a quiet non-pawn move increments the half-move clock") {
      val b = applyMove("4k3/8/8/8/8/4N3/8/4K3 w - - 5 10", "e3d5")
      assertTrue(b.halfMoveClock == 6)
    },

    // ── promotion picks the chosen piece (under-promotion) ───────────────────
    test("under-promotion to knight places a knight, not a queen") {
      val b  = applyMove("4k3/4P3/8/8/8/8/8/4K3 w - - 0 1", "e7e8n")
      val e8 = maichess.movevalidator.rules.Square.fromAlgebraic("e8").get
      assertTrue(b.pieceAt(e8).exists(_.pieceType == PieceType.Knight))
    },
  )
