package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{FenParser, LegalityFilter}

// Targets the LegalityFilter rule mutants: the castling-through/out-of-check guard,
// the pawn-attack direction, and the king-absent fold defaults. MoveGeneratorSpec only
// covers move counts and a single pin.
object LegalityFilterSpec extends ZIOSpecDefault:

  private def board(fen: String) =
    FenParser.parse(fen).fold(e => throw new RuntimeException(e), identity)

  private def legalUcis(fen: String): Set[String] =
    LegalityFilter.legalMoves(board(fen)).map(_.value).toSet

  def spec = suite("LegalityFilterSpec")(

    // ── castling legality ────────────────────────────────────────────────────
    test("unobstructed, unattacked castling is legal both sides") {
      val moves = legalUcis("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
      assertTrue(moves.contains("e1g1"), moves.contains("e1c1"))
    },
    test("kingside castling through an attacked f1 is illegal") {
      // Black rook on f2 attacks f1, the square the king crosses; O-O must be dropped
      // while the king itself is not in check and g1 is safe.
      val moves = legalUcis("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1")
      assertTrue(!moves.contains("e1g1"))
    },
    test("queenside castling through an attacked d1 is illegal") {
      val moves = legalUcis("4k3/8/8/8/8/8/3r4/R3K2R w KQ - 0 1")
      assertTrue(!moves.contains("e1c1"))
    },
    test("castling out of check is illegal") {
      // Black rook on e8 checks the king down the e-file; neither side may castle even
      // though the destination squares are themselves safe.
      val moves = legalUcis("4r2k/8/8/8/8/8/8/R3K2R w KQ - 0 1")
      assertTrue(!moves.contains("e1g1"), !moves.contains("e1c1"))
    },

    // ── pawn attack direction ────────────────────────────────────────────────
    test("a black pawn checks diagonally downward") {
      // Black pawn d5 attacks e4 (and c4); the white king on e4 is in check.
      assertTrue(LegalityFilter.isCheck(board("4k3/8/8/3p4/4K3/8/8/8 w - - 0 1")))
    },
    test("a white pawn checks diagonally upward") {
      // White pawn d7 attacks e8; the black king on e8 is in check.
      assertTrue(LegalityFilter.isCheck(board("4k3/3P4/8/8/8/8/8/4K3 b - - 0 1")))
    },

    // ── king-absent fold defaults ────────────────────────────────────────────
    test("isCheck is false when the side to move has no king") {
      assertTrue(!LegalityFilter.isCheck(board("4k3/8/8/8/8/8/4R3/8 w - - 0 1")))
    },
    test("moves are not all filtered out when the moving side has no king") {
      // With no white king, no move can leave a (nonexistent) king in check, so the
      // white rook still has legal moves.
      assertTrue(legalUcis("4k3/8/8/8/8/8/4R3/8 w - - 0 1").nonEmpty)
    },
  )
