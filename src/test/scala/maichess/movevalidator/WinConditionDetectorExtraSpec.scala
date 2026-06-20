package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.domain.GameResult
import maichess.movevalidator.rules.{FenParser, WinConditionDetector}

// Pins down the WinConditionDetector boundaries the happy-path spec leaves alive: the
// 100-ply fifty-move threshold (on a piece-ful position so it cannot also read as
// insufficient material) and the bishop-pair conditions of insufficient material.
object WinConditionDetectorExtraSpec extends ZIOSpecDefault:

  private def detect(fen: String): GameResult =
    FenParser.parse(fen).fold(e => throw new RuntimeException(e), WinConditionDetector.detect)

  def spec = suite("WinConditionDetectorExtraSpec")(

    // ── fifty-move boundary (queens present ⇒ not insufficient material) ──────
    test("half-move clock exactly 100 triggers the fifty-move rule") {
      assertTrue(detect("q3k3/8/8/8/8/8/8/Q3K3 w - - 100 60") == GameResult.FiftyMoveRule)
    },
    test("half-move clock 99 does not trigger the fifty-move rule") {
      assertTrue(detect("q3k3/8/8/8/8/8/8/Q3K3 w - - 99 60") == GameResult.None)
    },
    test("half-move clock above 100 still triggers the fifty-move rule") {
      // The threshold is `>= 100`, not `== 100`: 101 plies must still be the fifty-move rule.
      assertTrue(detect("q3k3/8/8/8/8/8/8/Q3K3 w - - 101 60") == GameResult.FiftyMoveRule)
    },

    // ── bishop-pair insufficient-material conditions ─────────────────────────
    test("opposite-coloured bishops on opposite square colours are not insufficient") {
      // a1 (dark-indexed) white bishop vs b1 (light-indexed) black bishop ⇒ the pair
      // does not share a square colour, so it is not the drawn bishop pair.
      assertTrue(detect("4k3/8/8/8/8/8/8/Bb2K3 w - - 0 1") == GameResult.None)
    },
    test("two same-side bishops are not the insufficient bishop pair") {
      assertTrue(detect("4k3/8/8/8/8/8/8/B1B1K3 w - - 0 1") == GameResult.None)
    },
    test("a bishop and a knight are not the insufficient bishop pair") {
      assertTrue(detect("4k3/8/8/8/8/8/8/B1N1K3 w - - 0 1") == GameResult.None)
    },
  )
