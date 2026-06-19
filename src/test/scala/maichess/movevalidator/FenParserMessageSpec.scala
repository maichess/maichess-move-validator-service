package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.FenParser

// Exact error-message assertions and uncovered-branch coverage for rules.FenParser
// (which decodes a FEN into a Board). FenParserSpec only asserts isLeft for the
// malformed cases, leaving the message strings and the NoCoverage error branches open.
object FenParserMessageSpec extends ZIOSpecDefault:

  def spec = suite("FenParserMessageSpec")(
    test("wrong field count message") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - -") == Left("FEN must have 6 space-separated fields, got: k6K/8/8/8/8/8/8/8 w - -"))
    },
    test("wrong rank count message") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8 w - - 0 1") == Left("Board must have 8 ranks, got 7"))
    },
    test("unknown piece char message") {
      assertTrue(FenParser.parse("X7/8/8/8/8/8/8/K6k w - - 0 1") == Left("Unknown piece char 'X'"))
    },
    test("file overflow message") {
      // Nine pieces on a rank push the file index to 8, which has no valid File.
      assertTrue(FenParser.parse("ppppppppp/8/8/8/8/8/8/K6k w - - 0 1") == Left("Invalid file 8"))
    },
    test("invalid active color message") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 x - - 0 1") == Left("Invalid active color 'x'"))
    },
    test("partly-valid castling field is rejected (forall, not exists)") {
      // "Kx" has a valid first char but an invalid second; forall must reject it.
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w Kx - 0 1") == Left("Invalid castling field 'Kx'"))
    },
    test("malformed en passant square message") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - zz 0 1") == Left("Invalid en passant square 'zz'"))
    },
    test("en passant on the wrong rank message") {
      // e4 is a well-formed square but not an en-passant rank (must be 3 or 6).
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - e4 0 1") == Left("Invalid en passant square 'e4'"))
    },
    test("en passant rank 3 is accepted") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - e3 0 1").isRight)
    },
    test("en passant rank 6 is accepted") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - e6 0 1").isRight)
    },
    test("invalid half-move clock message") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - - x 1") == Left("Invalid half-move clock 'x'"))
    },
    test("invalid full-move number message") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - - 0 z") == Left("Invalid full-move number 'z'"))
    },
    test("dash en passant is accepted") {
      assertTrue(FenParser.parse("k6K/8/8/8/8/8/8/8 w - - 0 1").isRight)
    },
  )
