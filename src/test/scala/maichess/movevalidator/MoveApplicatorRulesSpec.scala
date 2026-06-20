package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{
  Board, CastlingRights, ChessMove, Color, FenParser, MoveApplicator, Piece, PieceType, Square,
}

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

  private def board(fen: String): Board =
    FenParser.parse(fen).fold(e => throw new RuntimeException(e), identity)

  private def sq(alg: String): Square = Square.fromAlgebraic(alg).get

  // A bare two-king board; both kings sit in a corner so the squares the degenerate
  // moves below touch are empty — this lets us drive MoveApplicator with a move whose
  // source square holds no piece, the one input that distinguishes `exists`/`forall`
  // and the en-passant capture flag.
  private def kingsOnly(half: Int): Board =
    Board(
      pieces          = Map(sq("a1") -> Piece(Color.White, PieceType.King),
                            sq("a8") -> Piece(Color.Black, PieceType.King)),
      sideToMove      = Color.White,
      castlingRights  = CastlingRights.none,
      enPassantSquare = None,
      halfMoveClock   = half,
      fullMoveNumber  = 1,
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

    // ── empty source square: pins the `exists` (not `forall`) piece probes and the
    //    en-passant capture flag, which only diverge when the source holds no piece ──
    test("a quiet move from an empty source square advances the half-move clock") {
      // isPawn must read false for an empty source (exists), so the clock increments.
      val moved = MoveApplicator(kingsOnly(half = 5), ChessMove.Normal(sq("c4"), sq("c5"), None))
      assertTrue(moved.halfMoveClock == 6)
    },
    test("a two-square move from an empty source square sets no en-passant target") {
      // The pawn probe is `exists`; an empty source must not yield an en-passant square.
      val moved = MoveApplicator(kingsOnly(half = 0), ChessMove.Normal(sq("c2"), sq("c4"), None))
      assertTrue(moved.enPassantSquare.isEmpty)
    },
    test("an en-passant move from an empty source square still zeroes the half-move clock") {
      // isEnPassant must hold even when the source is empty (isPawn is false there), so
      // the clock resets — distinguishing the `true` literal in isEnPassant from `false`.
      val moved = MoveApplicator(kingsOnly(half = 7), ChessMove.EnPassant(sq("c5"), sq("d6"), sq("d5")))
      assertTrue(moved.halfMoveClock == 0)
    },

    // ── fromUci move classification ──────────────────────────────────────────
    test("fromUci rejects a UCI string whose length is neither 4 nor 5") {
      // Length 6: the length guard must reject it before any square parsing.
      assertTrue(MoveApplicator.fromUci(board(blackRooks), "e8g8xy").isEmpty)
    },
    test("fromUci treats e1→g1 as a normal move when no king stands on e1") {
      // isKing is `exists`: with e1 empty it is false, so this is not castling.
      MoveApplicator.fromUci(board("4k3/8/8/8/8/8/8/R6R w - - 0 1"), "e1g1") match
        case Some(_: ChessMove.Normal) => assertTrue(true)
        case other                     => assertTrue(false) ?? s"expected Normal, got $other"
    },
    test("fromUci does not treat an empty source as an en-passant capture") {
      // e6 is the en-passant square but d5 is empty; isPawn (exists) is false ⇒ normal.
      MoveApplicator.fromUci(board("4k3/8/8/8/8/8/8/4K3 w - e6 0 1"), "d5e6") match
        case Some(_: ChessMove.Normal) => assertTrue(true)
        case other                     => assertTrue(false) ?? s"expected Normal, got $other"
    },
    test("a pawn double-push to a non-en-passant square is a normal move, not en passant") {
      // isEp = isPawn && enPassant.contains(to); with `&&` (not `||`) a true isPawn alone
      // must not make e2e4 an en-passant move when there is no en-passant square.
      MoveApplicator.fromUci(board("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"), "e2e4") match
        case Some(_: ChessMove.Normal) => assertTrue(true)
        case other                     => assertTrue(false) ?? s"expected Normal, got $other"
    },
    test("a non-castling king move is a normal move, not castling") {
      // The isCastlingTarget fall-through must stay `false`: e1e2 is a plain king move.
      MoveApplicator.fromUci(board("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), "e1e2") match
        case Some(_: ChessMove.Normal) => assertTrue(true)
        case other                     => assertTrue(false) ?? s"expected Normal, got $other"
    },
    test("fromUci resolves white queenside castling with the a1 rook") {
      // Pins the ("e1","c1") castling-target literals and the queenside rook-from file.
      MoveApplicator.fromUci(board("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1"), "e1c1") match
        case Some(ChessMove.Castle(from, to, rookFrom, rookTo)) =>
          assertTrue(
            from.toAlgebraic == "e1",
            to.toAlgebraic == "c1",
            rookFrom.toAlgebraic == "a1",
            rookTo.toAlgebraic == "d1",
          )
        case other => assertTrue(false) ?? s"expected Castle, got $other"
    },
    test("fromUci resolves black kingside castling with the h8 rook") {
      // Pins the ("e8","g8") castling-target literals and the kingside rook-from file.
      MoveApplicator.fromUci(board("4k2r/8/8/8/8/8/8/4K3 b k - 0 1"), "e8g8") match
        case Some(ChessMove.Castle(from, to, rookFrom, rookTo)) =>
          assertTrue(
            from.toAlgebraic == "e8",
            to.toAlgebraic == "g8",
            rookFrom.toAlgebraic == "h8",
            rookTo.toAlgebraic == "f8",
          )
        case other => assertTrue(false) ?? s"expected Castle, got $other"
    },
  )
