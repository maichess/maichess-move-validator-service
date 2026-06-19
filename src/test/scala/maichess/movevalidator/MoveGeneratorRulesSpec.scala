package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{FenParser, LegalityFilter, MoveGenerator}

// Targets the MoveGenerator mutants around castling generation (coordinate strings and
// the right/empty guards) and pawn promotion (the promoChar table and the promotion
// rank), which MoveGeneratorSpec's move-count checks do not pin down.
object MoveGeneratorRulesSpec extends ZIOSpecDefault:

  private def board(fen: String) =
    FenParser.parse(fen).fold(e => throw new RuntimeException(e), identity)

  private def pseudoUcis(fen: String): Set[String] =
    MoveGenerator.pseudoLegal(board(fen)).map(MoveGenerator.toUci(_).value).toSet

  private def legalUcis(fen: String): Set[String] =
    LegalityFilter.legalMoves(board(fen)).map(_.value).toSet

  def spec = suite("MoveGeneratorRulesSpec")(

    // ── black castling generation (coordinate strings) ───────────────────────
    test("black may castle both sides when unobstructed") {
      val moves = legalUcis("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1")
      assertTrue(moves.contains("e8g8"), moves.contains("e8c8"))
    },

    // ── castling-right and empty-square guards ───────────────────────────────
    test("no castling is generated without the rights") {
      val moves = pseudoUcis("4k3/8/8/8/8/8/8/R3K2R w - - 0 1")
      assertTrue(!moves.contains("e1g1"), !moves.contains("e1c1"))
    },
    test("a blocked queenside is not generated while kingside still is") {
      // A bishop on d1 occupies a queenside transit square.
      val moves = pseudoUcis("4k3/8/8/8/8/8/8/R2BK2R w KQ - 0 1")
      assertTrue(!moves.contains("e1c1"), moves.contains("e1g1"))
    },

    // ── pawn promotion generation (promoChar + promotion rank) ───────────────
    test("a white pawn generates all four promotions on the 8th rank") {
      val moves = pseudoUcis("k7/4P3/8/8/8/8/8/4K3 w - - 0 1")
      assertTrue(
        moves.contains("e7e8q"),
        moves.contains("e7e8r"),
        moves.contains("e7e8b"),
        moves.contains("e7e8n"),
      )
    },
    test("a black pawn promotes on the 1st rank") {
      val moves = pseudoUcis("4k3/8/8/8/8/8/4p3/K7 b - - 0 1")
      assertTrue(
        moves.contains("e2e1q"),
        moves.contains("e2e1n"),
      )
    },
    test("a non-promoting pawn push carries no promotion suffix") {
      val moves = pseudoUcis("4k3/8/8/8/8/4P3/8/4K3 w - - 0 1")
      assertTrue(moves.contains("e3e4"), !moves.contains("e3e4q"))
    },
  )
