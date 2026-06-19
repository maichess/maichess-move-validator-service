package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.domain.Fen

// Exact error-message and boundary assertions for domain Fen.parse. The existing
// FenParserSpec only checks isLeft/isRight, which leaves the message strings and the
// field-validation boundaries unverified.
object FenMessageSpec extends ZIOSpecDefault:

  // Base position whose every field is individually valid, so each malformed variant
  // isolates a single failing field.
  private val base = "k6K/8/8/8/8/8/8/8 w - - 0 1"

  def spec = suite("FenMessageSpec")(

    // ── exact error messages ─────────────────────────────────────────────────
    test("wrong field count message") {
      assertTrue(Fen.parse("k6K w - - 0") == Left("FEN must have 6 space-separated fields, got: k6K w - - 0"))
    },
    test("wrong rank count message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8 w - - 0 1") == Left("Board must have 8 ranks, got 7"))
    },
    test("unknown piece char message") {
      assertTrue(Fen.parse("X7/8/8/8/8/8/8/K6k w - - 0 1") == Left("Unknown piece char 'X' in rank 0"))
    },
    test("wrong file count message") {
      assertTrue(Fen.parse("ppp/8/8/8/8/8/8/K6k w - - 0 1") == Left("Rank 0 has 3 files, expected 8"))
    },
    test("invalid side-to-move message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 x - - 0 1") == Left("Active color must be 'w' or 'b', got 'x'"))
    },
    test("invalid castling field message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w XY - 0 1") == Left("Invalid castling field: 'XY'"))
    },
    test("invalid en passant square message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - e4 0 1") == Left("Invalid en passant square: 'e4'"))
    },
    test("invalid half-move clock message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - - x 1") == Left("Invalid half-move clock: 'x'"))
    },
    test("negative half-move clock message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - - -1 1") == Left("Invalid half-move clock: '-1'"))
    },
    test("invalid full-move number message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - - 0 z") == Left("Invalid full-move number: 'z'"))
    },
    test("zero full-move number message") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - - 0 0") == Left("Invalid full-move number: '0'"))
    },

    // ── side-to-move both values accepted ────────────────────────────────────
    test("white to move is accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - - 0 1").isRight)
    },
    test("black to move is accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 b - - 0 1").isRight)
    },

    // ── castling field variants ──────────────────────────────────────────────
    test("dash castling is accepted") {
      assertTrue(Fen.parse(base).isRight)
    },
    test("full castling rights are accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w KQkq - 0 1").isRight)
    },
    test("empty castling token is rejected") {
      // s.nonEmpty guards the forall — an empty string must not slip through.
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w  - 0 1").isLeft)
    },

    // ── en-passant boundary matrix (file a–h, rank 3 or 6) ───────────────────
    test("en passant a3 (lower file, rank 3) accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - a3 0 1").isRight)
    },
    test("en passant h3 (upper file) accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - h3 0 1").isRight)
    },
    test("en passant a6 (rank 6) accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - a6 0 1").isRight)
    },
    test("en passant h6 accepted") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - h6 0 1").isRight)
    },
    test("en passant file above h is rejected") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - i3 0 1").isLeft)
    },
    test("en passant file below a is rejected") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - `3 0 1").isLeft)
    },
    test("en passant rank 4 is rejected") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - a4 0 1").isLeft)
    },
    test("en passant rank 5 is rejected") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - a5 0 1").isLeft)
    },
    test("en passant single-character square is rejected") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - a 0 1").isLeft)
    },
    test("en passant three-character square is rejected") {
      assertTrue(Fen.parse("k6K/8/8/8/8/8/8/8 w - a3x 0 1").isLeft)
    },
  )
