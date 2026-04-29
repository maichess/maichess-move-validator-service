package maichess.movevalidator

import io.grpc.Status
import zio.test.*
import zio.ZIO
import maichess.movevalidator.grpc.MovesServiceImpl
import maichess.movevalidator.service.ValidatorServiceLive
import maichess.move_validator.v1.moves.moves.{
  ConvertSequenceToSanRequest,
  GetLegalMovesRequest,
  GetLegalMovesSanRequest,
  ValidateMoveRequest,
  ValidateMoveSanRequest,
}

// NOTE: This spec depends on ZioMoves.ZMoves being present in platform-protos.
// See CONTRACT_NOTES.md for the blocker and proposed fix.
object MovesServiceImplSpec extends ZIOSpecDefault:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  private val makeImpl = ZIO.succeed(new MovesServiceImpl(new ValidatorServiceLive))

  def spec = suite("MovesServiceImplSpec")(
    test("ValidateMove with valid move returns valid = true, non-empty resulting_fen, and non-empty position_history") {
      makeImpl.flatMap(_.validateMove(ValidateMoveRequest(fen = startFen, move = "e2e4"))).map { res =>
        assertTrue(res.valid, res.resultingFen.nonEmpty, res.positionHistory.nonEmpty)
      }
    },
    test("ValidateMove detects threefold repetition") {
      val fen = "8/8/4k3/8/8/4K3/8/8 w - - 2 10"
      val repeatedKey = "8/8/4k3/8/8/5K2/8/8 b - -"
      import maichess.move_validator.v1.moves.moves.{GameResult => ProtoGameResult}
      makeImpl.flatMap(_.validateMove(ValidateMoveRequest(
        fen = fen, move = "e3f3", positionHistory = Seq(repeatedKey, repeatedKey)
      ))).map { res =>
        assertTrue(res.valid, res.gameResult == ProtoGameResult.GAME_RESULT_THREEFOLD_REPETITION)
      }
    },
    test("ValidateMove with illegal move returns valid = false and non-empty reason") {
      makeImpl.flatMap(_.validateMove(ValidateMoveRequest(fen = startFen, move = "e2e5"))).map { res =>
        assertTrue(!res.valid, res.reason.nonEmpty)
      }
    },
    test("ValidateMove with malformed FEN returns valid = false") {
      makeImpl.flatMap(_.validateMove(ValidateMoveRequest(fen = "not a fen", move = "e2e4"))).map { res =>
        assertTrue(!res.valid)
      }
    },
    test("GetLegalMoves from starting position returns exactly 20 moves") {
      makeImpl.flatMap(_.getLegalMoves(GetLegalMovesRequest(fen = startFen))).map { res =>
        assertTrue(res.moves.size == 20)
      }
    },
    test("GetLegalMoves with malformed FEN returns INVALID_ARGUMENT status") {
      makeImpl.flatMap(_.getLegalMoves(GetLegalMovesRequest(fen = "garbage")).either).map {
        case Left(status) => assertTrue(status.getCode == Status.Code.INVALID_ARGUMENT)
        case Right(_)     => assertTrue(false) ?? "expected INVALID_ARGUMENT status"
      }
    },

    // ── ValidateMoveSan ───────────────────────────────────────────────────────
    test("ValidateMoveSan with valid SAN returns valid = true and non-empty uci_move") {
      makeImpl.flatMap(_.validateMoveSan(ValidateMoveSanRequest(fen = startFen, move = "e4"))).map { res =>
        assertTrue(res.valid, res.uciMove == "e2e4", res.resultingFen.nonEmpty)
      }
    },
    test("ValidateMoveSan with unknown SAN returns valid = false") {
      makeImpl.flatMap(_.validateMoveSan(ValidateMoveSanRequest(fen = startFen, move = "Nf6"))).map { res =>
        assertTrue(!res.valid, res.reason.nonEmpty)
      }
    },
    test("ValidateMoveSan with malformed FEN returns valid = false") {
      makeImpl.flatMap(_.validateMoveSan(ValidateMoveSanRequest(fen = "garbage", move = "e4"))).map { res =>
        assertTrue(!res.valid)
      }
    },

    // ── GetLegalMovesSan ──────────────────────────────────────────────────────
    test("GetLegalMovesSan from starting position returns 20 moves with both notations") {
      makeImpl.flatMap(_.getLegalMovesSan(GetLegalMovesSanRequest(fen = startFen))).map { res =>
        assertTrue(
          res.moves.size == 20,
          res.moves.forall(m => m.uci.nonEmpty && m.san.nonEmpty),
          res.moves.exists(m => m.uci == "e2e4" && m.san == "e4"),
        )
      }
    },
    test("GetLegalMovesSan with malformed FEN returns INVALID_ARGUMENT status") {
      makeImpl.flatMap(_.getLegalMovesSan(GetLegalMovesSanRequest(fen = "garbage")).either).map {
        case Left(status) => assertTrue(status.getCode == Status.Code.INVALID_ARGUMENT)
        case Right(_)     => assertTrue(false) ?? "expected INVALID_ARGUMENT status"
      }
    },

    // ── ConvertSequenceToSan ──────────────────────────────────────────────────
    test("ConvertSequenceToSan converts Fool's mate sequence correctly") {
      val req = ConvertSequenceToSanRequest(
        startingFen = startFen,
        uciMoves    = Seq("f2f3", "e7e5", "g2g4", "d8h4"),
      )
      makeImpl.flatMap(_.convertSequenceToSan(req)).map { res =>
        assertTrue(res.sanMoves == Seq("f3", "e5", "g4", "Qh4#"))
      }
    },
    test("ConvertSequenceToSan with empty sequence returns empty list") {
      val req = ConvertSequenceToSanRequest(startingFen = startFen, uciMoves = Seq.empty)
      makeImpl.flatMap(_.convertSequenceToSan(req)).map { res =>
        assertTrue(res.sanMoves.isEmpty)
      }
    },
    test("ConvertSequenceToSan with illegal move returns INVALID_ARGUMENT status") {
      val req = ConvertSequenceToSanRequest(startingFen = startFen, uciMoves = Seq("e2e5"))
      makeImpl.flatMap(_.convertSequenceToSan(req).either).map {
        case Left(status) => assertTrue(status.getCode == Status.Code.INVALID_ARGUMENT)
        case Right(_)     => assertTrue(false) ?? "expected INVALID_ARGUMENT status"
      }
    },
    test("ConvertSequenceToSan with malformed starting FEN returns INVALID_ARGUMENT status") {
      val req = ConvertSequenceToSanRequest(startingFen = "garbage", uciMoves = Seq("e2e4"))
      makeImpl.flatMap(_.convertSequenceToSan(req).either).map {
        case Left(status) => assertTrue(status.getCode == Status.Code.INVALID_ARGUMENT)
        case Right(_)     => assertTrue(false) ?? "expected INVALID_ARGUMENT status"
      }
    },
  )
