package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.domain.UciMove

object UciMoveSpec extends ZIOSpecDefault:

  def spec = suite("UciMoveSpec")(
    test("parses valid 4-char move") {
      assertTrue(UciMove.parse("e2e4") == Right(UciMove("e2e4")))
    },
    test("parses valid 4-char capture") {
      assertTrue(UciMove.parse("d5e6") == Right(UciMove("d5e6")))
    },
    test("parses promotion to queen") {
      assertTrue(UciMove.parse("e7e8q") == Right(UciMove("e7e8q")))
    },
    test("parses promotion to rook") {
      assertTrue(UciMove.parse("a7a8r") == Right(UciMove("a7a8r")))
    },
    test("parses promotion to bishop") {
      assertTrue(UciMove.parse("h7h8b") == Right(UciMove("h7h8b")))
    },
    test("parses promotion to knight") {
      assertTrue(UciMove.parse("b2b1n") == Right(UciMove("b2b1n")))
    },
    test("rejects move that is too short") {
      assertTrue(UciMove.parse("e2e").isLeft)
    },
    test("rejects move that is too long") {
      assertTrue(UciMove.parse("e2e4q5").isLeft)
    },
    test("rejects out-of-range file in from-square") {
      assertTrue(UciMove.parse("i2e4").isLeft)
    },
    test("rejects out-of-range rank in from-square") {
      assertTrue(UciMove.parse("e9e4").isLeft)
    },
    test("rejects out-of-range file in to-square") {
      assertTrue(UciMove.parse("e2z4").isLeft)
    },
    test("rejects out-of-range rank in to-square") {
      assertTrue(UciMove.parse("e2e0").isLeft)
    },
    test("rejects invalid promotion piece") {
      assertTrue(UciMove.parse("e7e8k").isLeft)
    },
    test("rejects invalid promotion piece pawn") {
      assertTrue(UciMove.parse("e7e8p").isLeft)
    },
  )
