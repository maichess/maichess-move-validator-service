package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{
  Board, ChessMove, FenParser, LegalityFilter, PieceType, SanNotation, Square,
}

object SanNotationSpec extends ZIOSpecDefault:

  private def board(fen: String): Board = FenParser.parse(fen).fold(e => throw new RuntimeException(e), identity)

  private def chessMoveFor(b: Board, uci: String): ChessMove =
    LegalityFilter.legalChessMoves(b)
      .find(m => m.from.toAlgebraic == uci.take(2) && m.to.toAlgebraic == uci.drop(2).take(2) &&
        (uci.length != 5 || {
          val promoChar = uci(4)
          m match
            case ChessMove.Normal(_, _, Some(pt)) =>
              (pt == PieceType.Queen  && promoChar == 'q') ||
              (pt == PieceType.Rook   && promoChar == 'r') ||
              (pt == PieceType.Bishop && promoChar == 'b') ||
              (pt == PieceType.Knight && promoChar == 'n')
            case _ => false
        })
      )
      .getOrElse(throw new RuntimeException(s"Move $uci not found in legal moves"))

  private val startFen   = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  private val promoFen   = "8/4P3/8/8/8/8/8/4K1k1 w - - 0 1"
  private val ksCastleFen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
  private val qsCastleFen = "r3kbnr/pppqpppp/2npb3/8/3PP3/2NB1N2/PPP2PPP/R1BQK2R b KQkq - 4 6"
  private val captureFen  = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
  private val epFen       = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3"
  // Two rooks on a1 and h1, can both reach d1 → file disambiguation
  private val fileDisFen  = "4k3/8/8/8/8/4K3/8/R6R w - - 0 1"
  // Two rooks on a1 and a7, can both reach a4 → rank disambiguation
  private val rankDisFen  = "4k3/R7/8/8/8/8/8/R5K1 w - - 0 1"
  // Three queens on a1/e1/a5 can all reach e5 → full-square disambiguation
  private val fullDisFen  = "8/8/7k/Q7/8/8/8/Q3QK2 w - - 0 1"
  // White queen can deliver check but not checkmate
  private val checkFen    = "4k3/8/8/8/Q7/8/8/4K3 w - - 0 1"
  // Fool's mate pre-final: black queen delivers checkmate
  private val mateFen     = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2"

  def spec = suite("SanNotationSpec")(

    // ── Pawn moves ────────────────────────────────────────────────────────────
    test("pawn push e4") {
      val b = board(startFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e2e4")) == "e4")
    },
    test("pawn push d4") {
      val b = board(startFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "d2d4")) == "d4")
    },
    test("pawn capture exd5") {
      val b = board(captureFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e4d5")) == "exd5")
    },
    test("en passant exd6") {
      val b = board(epFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e5d6")) == "exd6")
    },

    // ── Promotions ────────────────────────────────────────────────────────────
    test("promotion e8=Q") {
      val b = board(promoFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e7e8q")) == "e8=Q")
    },
    test("promotion e8=R") {
      val b = board(promoFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e7e8r")) == "e8=R")
    },
    test("promotion e8=B") {
      val b = board(promoFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e7e8b")) == "e8=B")
    },
    test("promotion e8=N") {
      val b = board(promoFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e7e8n")) == "e8=N")
    },

    // ── Piece moves ───────────────────────────────────────────────────────────
    test("knight Nf3") {
      val b = board(startFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "g1f3")) == "Nf3")
    },
    test("knight Nc3") {
      val b = board(startFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "b1c3")) == "Nc3")
    },

    // ── Castling ──────────────────────────────────────────────────────────────
    test("kingside castling O-O") {
      val b = board(ksCastleFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e1g1")) == "O-O")
    },
    test("queenside castling O-O-O") {
      val b = board(qsCastleFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e8c8")) == "O-O-O")
    },

    // ── Disambiguation ────────────────────────────────────────────────────────
    test("file disambiguation Rad1") {
      val b = board(fileDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "a1d1")) == "Rad1")
    },
    test("file disambiguation Rhd1") {
      val b = board(fileDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "h1d1")) == "Rhd1")
    },
    test("rank disambiguation R1a4") {
      val b = board(rankDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "a1a4")) == "R1a4")
    },
    test("rank disambiguation R7a4") {
      val b = board(rankDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "a7a4")) == "R7a4")
    },
    test("full-square disambiguation Qa1e5") {
      val b = board(fullDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "a1e5")) == "Qa1e5")
    },
    test("full-square disambiguation Qee5") {
      val b = board(fullDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "e1e5")) == "Qee5")
    },
    test("full-square disambiguation Q5e5") {
      val b = board(fullDisFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "a5e5")) == "Q5e5")
    },

    // ── Check / checkmate suffixes ────────────────────────────────────────────
    test("check suffix Qd7+") {
      val b = board(checkFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "a4d7")) == "Qd7+")
    },
    test("checkmate suffix Qh4#") {
      val b = board(mateFen)
      assertTrue(SanNotation.toSan(b, chessMoveFor(b, "d8h4")) == "Qh4#")
    },

    // ── Empty source square (coverage of fold fallback) ───────────────────────
    test("toSan with move from empty square returns empty string") {
      val b    = board(startFen)
      val from = Square.fromAlgebraic("d4").getOrElse(throw new RuntimeException("d4"))
      val to   = Square.fromAlgebraic("d5").getOrElse(throw new RuntimeException("d5"))
      val move = ChessMove.Normal(from, to, None)
      assertTrue(SanNotation.toSan(b, move) == "")
    },

    // ── pieceChar / promoChar coverage for unreachable branches ──────────────
    test("pieceChar returns empty string for Pawn") {
      assertTrue(SanNotation.pieceChar(PieceType.Pawn) == "")
    },
    test("promoChar returns empty string for non-promotion piece type") {
      assertTrue(SanNotation.promoChar(PieceType.King) == "")
    },

    // ── fromSan round-trips ───────────────────────────────────────────────────
    test("fromSan round-trips pawn push e4") {
      val b      = board(startFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "e2e4")
      assertTrue(SanNotation.fromSan(b, "e4", legal).contains(target))
    },
    test("fromSan round-trips pawn capture exd5") {
      val b      = board(captureFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "e4d5")
      assertTrue(SanNotation.fromSan(b, "exd5", legal).contains(target))
    },
    test("fromSan round-trips en passant exd6") {
      val b      = board(epFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "e5d6")
      assertTrue(SanNotation.fromSan(b, "exd6", legal).contains(target))
    },
    test("fromSan round-trips promotion e8=Q") {
      val b      = board(promoFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "e7e8q")
      assertTrue(SanNotation.fromSan(b, "e8=Q", legal).contains(target))
    },
    test("fromSan round-trips Nf3") {
      val b      = board(startFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "g1f3")
      assertTrue(SanNotation.fromSan(b, "Nf3", legal).contains(target))
    },
    test("fromSan round-trips O-O") {
      val b      = board(ksCastleFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "e1g1")
      assertTrue(SanNotation.fromSan(b, "O-O", legal).contains(target))
    },
    test("fromSan round-trips O-O-O") {
      val b      = board(qsCastleFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "e8c8")
      assertTrue(SanNotation.fromSan(b, "O-O-O", legal).contains(target))
    },
    test("fromSan round-trips file disambiguation Rad1") {
      val b      = board(fileDisFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "a1d1")
      assertTrue(SanNotation.fromSan(b, "Rad1", legal).contains(target))
    },
    test("fromSan round-trips rank disambiguation R1a4") {
      val b      = board(rankDisFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "a1a4")
      assertTrue(SanNotation.fromSan(b, "R1a4", legal).contains(target))
    },
    test("fromSan round-trips full-square disambiguation Qa1e5") {
      val b      = board(fullDisFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "a1e5")
      assertTrue(SanNotation.fromSan(b, "Qa1e5", legal).contains(target))
    },
    test("fromSan accepts check suffix stripped: Qd7 matches Qd7+") {
      val b     = board(checkFen)
      val legal = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "a4d7")
      assertTrue(SanNotation.fromSan(b, "Qd7", legal).contains(target))
    },
    test("fromSan accepts checkmate suffix stripped: Qh4 matches Qh4#") {
      val b      = board(mateFen)
      val legal  = LegalityFilter.legalChessMoves(b)
      val target = chessMoveFor(b, "d8h4")
      assertTrue(SanNotation.fromSan(b, "Qh4", legal).contains(target))
    },
    test("fromSan returns None for illegal SAN") {
      val b     = board(startFen)
      val legal = LegalityFilter.legalChessMoves(b)
      assertTrue(SanNotation.fromSan(b, "Nf6", legal).isEmpty)
    },
    test("fromSan returns None for completely invalid string") {
      val b     = board(startFen)
      val legal = LegalityFilter.legalChessMoves(b)
      assertTrue(SanNotation.fromSan(b, "xyz", legal).isEmpty)
    },
  )
