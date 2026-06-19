package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.Square

// Exercises the File/Rank range guards (the && in File.fromChar and Rank.fromChar) via
// Square.fromAlgebraic at every boundary.
object BoardSpec extends ZIOSpecDefault:

  def spec = suite("BoardSpec")(
    test("all four corner squares parse") {
      assertTrue(
        Square.fromAlgebraic("a1").isDefined,
        Square.fromAlgebraic("h1").isDefined,
        Square.fromAlgebraic("a8").isDefined,
        Square.fromAlgebraic("h8").isDefined,
      )
    },
    test("a file above 'h' is rejected") {
      assertTrue(Square.fromAlgebraic("i1").isEmpty)
    },
    test("a file below 'a' is rejected") {
      assertTrue(Square.fromAlgebraic("`1").isEmpty)
    },
    test("a rank above 8 is rejected") {
      assertTrue(Square.fromAlgebraic("a9").isEmpty)
    },
    test("a rank below 1 is rejected") {
      assertTrue(Square.fromAlgebraic("a0").isEmpty)
    },
    test("a valid square round-trips to algebraic") {
      assertTrue(Square.fromAlgebraic("e4").map(_.toAlgebraic) == Some("e4"))
    },
  )
