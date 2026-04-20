package maichess.movevalidator

import zio.test.*
import zio.ZIO
import maichess.movevalidator.domain.{Fen, GameResult, UciMove, ValidationResult}
import maichess.movevalidator.service.{ValidatorService, ValidatorServiceLive}

object ValidatorServiceSpec extends ZIOSpecDefault:

  private val startFen        = Fen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
  // Position after 1. f3 e5 2. g4 — Black plays Qd8h4# next
  private val preMateBlackFen = Fen("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2")
  private val invalidFen      = Fen("not valid fen")

  private def svc = ZIO.service[ValidatorService]

  def spec = suite("ValidatorServiceSpec")(
    test("valid move returns Valid with non-empty resulting FEN") {
      svc.flatMap(_.validateMove(startFen, UciMove("e2e4"))).map {
        case ValidationResult.Valid(fen, _) => assertTrue(fen.value.nonEmpty)
        case ValidationResult.Invalid(r)    => assertTrue(false) ?? r
      }
    },
    test("illegal move returns Invalid with reason") {
      // Pawn backward: e2→e1 is never legal
      val fen = Fen("8/8/8/8/8/8/4P3/4K2k w - - 0 1")
      svc.flatMap(_.validateMove(fen, UciMove("e2e1"))).map {
        case ValidationResult.Invalid(r) => assertTrue(r.nonEmpty)
        case ValidationResult.Valid(_, _) => assertTrue(false) ?? "backward pawn is illegal"
      }
    },
    test("invalid FEN returns failed ZIO") {
      svc.flatMap(_.validateMove(invalidFen, UciMove("e2e4")).either)
        .map(r => assertTrue(r.isLeft))
    },
    test("invalid UCI move returns Invalid") {
      svc.flatMap(_.validateMove(startFen, UciMove("zzz1"))).map {
        case ValidationResult.Invalid(_)  => assertTrue(true)
        case ValidationResult.Valid(_, _) => assertTrue(false) ?? "invalid UCI should be invalid"
      }
    },
    test("move delivering checkmate returns Valid with BlackWon") {
      // Fool's mate: black plays Qh4 delivering checkmate
      svc.flatMap(_.validateMove(preMateBlackFen, UciMove("d8h4"))).map {
        case ValidationResult.Valid(_, gr) => assertTrue(gr == GameResult.BlackWon)
        case ValidationResult.Invalid(r)   => assertTrue(false) ?? r
      }
    },
    test("legalMoves from starting position returns 20 moves") {
      svc.flatMap(_.legalMoves(startFen)).map(moves => assertTrue(moves.size == 20))
    },
    test("legalMoves with invalid FEN returns failed ZIO") {
      svc.flatMap(_.legalMoves(invalidFen).either)
        .map(r => assertTrue(r.isLeft))
    },
  ).provide(ValidatorServiceLive.layer)
