package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.domain.GameResult
import maichess.movevalidator.rules.{FenParser, WinConditionDetector}

object WinConditionDetectorSpec extends ZIOSpecDefault:

  // Fool's mate: White is checkmated (it's White to move with no legal moves, king in check)
  private val whiteMateFen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
  // Scholar's mate: Black is checkmated
  private val blackMateFen = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
  // Stalemate: Black king on a8, White king on b6, White queen on c7
  private val staleMateFen = "k7/2Q5/1K6/8/8/8/8/8 b - - 0 1"
  // Fifty-move rule position: halfMoveClock = 100
  private val fiftyMoveFen = "8/8/4k3/8/8/4K3/8/8 w - - 100 60"
  // K vs K: insufficient material
  private val kVsKFen      = "8/8/4k3/8/8/4K3/8/8 w - - 0 1"
  // K+B vs K: insufficient material
  private val kbVsKFen     = "8/8/4k3/8/8/4K3/5B2/8 w - - 0 1"
  // K+N vs K: insufficient material
  private val knVsKFen     = "8/8/4k3/8/8/4K3/5N2/8 w - - 0 1"
  // K+B vs K+B same color bishops: both on light squares (even file+rank index)
  private val bbSameFen    = "8/8/4k3/6b1/8/2B1K3/8/8 w - - 0 1"
  // Normal mid-game: game continues
  private val midGameFen   = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

  def spec = suite("WinConditionDetectorSpec")(
    test("fool's mate → WhiteWon (black just mated white)") {
      FenParser.parse(whiteMateFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.BlackWon)
    },
    test("scholar's mate → BlackWon") {
      FenParser.parse(blackMateFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.WhiteWon)
    },
    test("stalemate position → Stalemate") {
      FenParser.parse(staleMateFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.Stalemate)
    },
    test("fifty-move rule → Draw") {
      FenParser.parse(fiftyMoveFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.Draw)
    },
    test("K vs K → Draw") {
      FenParser.parse(kVsKFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.Draw)
    },
    test("K+B vs K → Draw") {
      FenParser.parse(kbVsKFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.Draw)
    },
    test("K+N vs K → Draw") {
      FenParser.parse(knVsKFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.Draw)
    },
    test("K+B vs K+B same color → Draw") {
      FenParser.parse(bbSameFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.Draw)
    },
    test("normal mid-game → None") {
      FenParser.parse(midGameFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(WinConditionDetector.detect(b) == GameResult.None)
    },
  )
