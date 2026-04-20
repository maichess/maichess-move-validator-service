package maichess.movevalidator

import io.grpc.Status
import zio.test.*
import zio.ZIO
import maichess.movevalidator.grpc.MovesServiceImpl
import maichess.movevalidator.service.ValidatorServiceLive
import maichess.movevalidator.v1.moves.{GetLegalMovesRequest, ValidateMoveRequest}

// NOTE: This spec depends on ZioMoves.ZMoves being present in platform-protos.
// See CONTRACT_NOTES.md for the blocker and proposed fix.
object MovesServiceImplSpec extends ZIOSpecDefault:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  private val makeImpl = ZIO.succeed(new MovesServiceImpl(new ValidatorServiceLive))

  def spec = suite("MovesServiceImplSpec")(
    test("ValidateMove with valid move returns valid = true and non-empty resulting_fen") {
      makeImpl.flatMap(_.validateMove(ValidateMoveRequest(fen = startFen, move = "e2e4"))).map { res =>
        assertTrue(res.valid, res.resultingFen.nonEmpty)
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
  )
