package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.domain.UciMove

// Exact error-message and square-boundary assertions for UciMove.parse. UciMoveSpec
// only checks isLeft for the negative cases.
object UciMoveMessageSpec extends ZIOSpecDefault:

  def spec = suite("UciMoveMessageSpec")(
    test("too-short message reports the length") {
      assertTrue(UciMove.parse("e2e") == Left("UCI move must be 4 or 5 characters, got 3: 'e2e'"))
    },
    test("too-long message reports the length") {
      assertTrue(UciMove.parse("e2e4q5") == Left("UCI move must be 4 or 5 characters, got 6: 'e2e4q5'"))
    },
    test("invalid-square message echoes the move") {
      assertTrue(UciMove.parse("i2e4") == Left("Invalid squares in UCI move: 'i2e4'"))
    },
    test("invalid destination-square message echoes the move") {
      assertTrue(UciMove.parse("e2z4") == Left("Invalid squares in UCI move: 'e2z4'"))
    },
    test("invalid promotion piece message echoes char and move") {
      assertTrue(UciMove.parse("e7e8k") == Left("Invalid promotion piece 'k' in UCI move: 'e7e8k'"))
    },
    test("promotion to pawn is an invalid promotion piece") {
      assertTrue(UciMove.parse("e7e8p") == Left("Invalid promotion piece 'p' in UCI move: 'e7e8p'"))
    },

    // ── square boundaries (file a–h, rank 1–8) ───────────────────────────────
    test("corner-to-corner a1h8 is valid") {
      assertTrue(UciMove.parse("a1h8") == Right(UciMove("a1h8")))
    },
    test("corner-to-corner h8a1 is valid") {
      assertTrue(UciMove.parse("h8a1") == Right(UciMove("h8a1")))
    },
    test("from-rank below 1 is rejected") {
      assertTrue(UciMove.parse("a0a2").isLeft)
    },
    test("to-rank above 8 is rejected") {
      assertTrue(UciMove.parse("a1a9").isLeft)
    },
    test("from-file below a is rejected") {
      assertTrue(UciMove.parse("`1a2").isLeft)
    },
    test("to-file above h is rejected") {
      assertTrue(UciMove.parse("a1i2").isLeft)
    },
  )
