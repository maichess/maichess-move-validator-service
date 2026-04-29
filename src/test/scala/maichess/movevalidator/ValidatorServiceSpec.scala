package maichess.movevalidator

import zio.test.*
import zio.ZIO
import maichess.movevalidator.domain.{Fen, GameResult, LegalMoveSan, UciMove, ValidateSanResult, ValidationResult}
import maichess.movevalidator.service.{ValidatorService, ValidatorServiceLive}

object ValidatorServiceSpec extends ZIOSpecDefault:

  private val startFen        = Fen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
  // Position after 1. f3 e5 2. g4 — Black plays Qd8h4# next
  private val preMateBlackFen = Fen("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2")
  private val invalidFen      = Fen("not valid fen")

  private def svc = ZIO.service[ValidatorService]

  def spec = suite("ValidatorServiceSpec")(
    test("valid move returns Valid with non-empty resulting FEN and non-empty position history") {
      svc.flatMap(_.validateMove(startFen, UciMove("e2e4"), Nil)).map {
        case ValidationResult.Valid(fen, _, history) => assertTrue(fen.value.nonEmpty, history.nonEmpty)
        case ValidationResult.Invalid(r)             => assertTrue(false) ?? r
      }
    },
    test("illegal move returns Invalid with reason") {
      // Pawn backward: e2→e1 is never legal
      val fen = Fen("8/8/8/8/8/8/4P3/4K2k w - - 0 1")
      svc.flatMap(_.validateMove(fen, UciMove("e2e1"), Nil)).map {
        case ValidationResult.Invalid(r)         => assertTrue(r.nonEmpty)
        case ValidationResult.Valid(_, _, _)     => assertTrue(false) ?? "backward pawn is illegal"
      }
    },
    test("invalid FEN returns failed ZIO") {
      svc.flatMap(_.validateMove(invalidFen, UciMove("e2e4"), Nil).either)
        .map(r => assertTrue(r.isLeft))
    },
    test("invalid UCI move returns Invalid") {
      svc.flatMap(_.validateMove(startFen, UciMove("zzz1"), Nil)).map {
        case ValidationResult.Invalid(_)      => assertTrue(true)
        case ValidationResult.Valid(_, _, _)  => assertTrue(false) ?? "invalid UCI should be invalid"
      }
    },
    test("move delivering checkmate returns Valid with BlackWon") {
      // Fool's mate: black plays Qh4 delivering checkmate
      svc.flatMap(_.validateMove(preMateBlackFen, UciMove("d8h4"), Nil)).map {
        case ValidationResult.Valid(_, gr, _) => assertTrue(gr == GameResult.BlackWon)
        case ValidationResult.Invalid(r)      => assertTrue(false) ?? r
      }
    },
    test("third repetition of a position returns ThreefoldRepetition") {
      // K vs K: white king moves e3→f3; supply history with two prior occurrences of that position key
      val fen = Fen("8/8/4k3/8/8/4K3/8/8 w - - 2 10")
      val repeatedKey = "8/8/4k3/8/8/5K2/8/8 b - -"
      svc.flatMap(_.validateMove(fen, UciMove("e3f3"), List(repeatedKey, repeatedKey))).map {
        case ValidationResult.Valid(_, gr, _) => assertTrue(gr == GameResult.ThreefoldRepetition)
        case ValidationResult.Invalid(r)      => assertTrue(false) ?? r
      }
    },
    test("pawn move resets position history regardless of prior entries") {
      svc.flatMap(_.validateMove(startFen, UciMove("e2e4"), List("some key", "another key"))).map {
        case ValidationResult.Valid(_, _, history) => assertTrue(history.size == 1)
        case ValidationResult.Invalid(r)           => assertTrue(false) ?? r
      }
    },
    test("legalMoves from starting position returns 20 moves") {
      svc.flatMap(_.legalMoves(startFen)).map(moves => assertTrue(moves.size == 20))
    },
    test("legalMoves with invalid FEN returns failed ZIO") {
      svc.flatMap(_.legalMoves(invalidFen).either)
        .map(r => assertTrue(r.isLeft))
    },

    // ── validateMoveSan ───────────────────────────────────────────────────────
    test("validateMoveSan with valid SAN returns Valid with uciMove") {
      svc.flatMap(_.validateMoveSan(startFen, "e4", Nil)).map {
        case ValidateSanResult.Valid(fen, _, _, uci) =>
          assertTrue(fen.value.nonEmpty, uci == "e2e4")
        case ValidateSanResult.Invalid(r) => assertTrue(false) ?? r
      }
    },
    test("validateMoveSan with unknown SAN returns Invalid with reason") {
      svc.flatMap(_.validateMoveSan(startFen, "Nf6", Nil)).map {
        case ValidateSanResult.Invalid(r)        => assertTrue(r.nonEmpty)
        case ValidateSanResult.Valid(_, _, _, _) => assertTrue(false) ?? "Nf6 is not legal for white"
      }
    },
    test("validateMoveSan with invalid FEN returns failed ZIO") {
      svc.flatMap(_.validateMoveSan(invalidFen, "e4", Nil).either)
        .map(r => assertTrue(r.isLeft))
    },

    // ── legalMovesSan ─────────────────────────────────────────────────────────
    test("legalMovesSan from starting position returns 20 moves") {
      svc.flatMap(_.legalMovesSan(startFen)).map(moves => assertTrue(moves.size == 20))
    },
    test("legalMovesSan each move has non-empty uci and san") {
      svc.flatMap(_.legalMovesSan(startFen)).map { moves =>
        assertTrue(moves.forall(m => m.uci.nonEmpty && m.san.nonEmpty))
      }
    },
    test("legalMovesSan includes e4 with SAN e4") {
      svc.flatMap(_.legalMovesSan(startFen)).map { moves =>
        assertTrue(moves.exists(m => m.uci == "e2e4" && m.san == "e4"))
      }
    },
    test("legalMovesSan with invalid FEN returns failed ZIO") {
      svc.flatMap(_.legalMovesSan(invalidFen).either)
        .map(r => assertTrue(r.isLeft))
    },

    // ── convertSequenceToSan ─────────────────────────────────────────────────
    test("convertSequenceToSan empty sequence returns empty list") {
      svc.flatMap(_.convertSequenceToSan(startFen, Nil))
        .map(sans => assertTrue(sans.isEmpty))
    },
    test("convertSequenceToSan Fool's mate sequence returns correct SAN list") {
      // f3, e5, g4, Qh4#
      val moves = List("f2f3", "e7e5", "g2g4", "d8h4")
      svc.flatMap(_.convertSequenceToSan(startFen, moves)).map { sans =>
        assertTrue(sans == List("f3", "e5", "g4", "Qh4#"))
      }
    },
    test("convertSequenceToSan illegal move at index 0 fails with index 0") {
      svc.flatMap(_.convertSequenceToSan(startFen, List("e2e5")).either).map {
        case Left(msg) => assertTrue(msg.contains("0"))
        case Right(_)  => assertTrue(false) ?? "expected failure"
      }
    },
    test("convertSequenceToSan illegal move at index 2 fails with index 2") {
      val moves = List("e2e4", "e7e5", "e4e7")
      svc.flatMap(_.convertSequenceToSan(startFen, moves).either).map {
        case Left(msg) => assertTrue(msg.contains("2"))
        case Right(_)  => assertTrue(false) ?? "expected failure"
      }
    },
    test("convertSequenceToSan with invalid FEN returns failed ZIO") {
      svc.flatMap(_.convertSequenceToSan(invalidFen, List("e2e4")).either)
        .map(r => assertTrue(r.isLeft))
    },
  ).provide(ValidatorServiceLive.layer)
