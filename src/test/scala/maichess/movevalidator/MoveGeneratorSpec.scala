package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{FenParser, LegalityFilter}

object MoveGeneratorSpec extends ZIOSpecDefault:

  private val startFen  = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  // White is in check: black rook on e1 side, must escape
  private val checkFen  = "4k3/8/8/8/8/8/8/4K2r w - - 0 1"
  // White knight on e4 pinned by black bishop on g6 (king on d3)
  private val pinFen    = "8/8/6b1/8/4N3/3K4/8/4k3 w - - 0 1"
  // Fool's mate position: Black to move, checkmate
  private val mateFen   = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"

  def spec = suite("MoveGeneratorSpec")(
    test("starting position has exactly 20 legal moves") {
      FenParser.parse(startFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(board) =>
          assertTrue(LegalityFilter.legalMoves(board).size == 20)
    },
    test("position in check has fewer moves (must escape check)") {
      FenParser.parse(checkFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(board) =>
          val moves = LegalityFilter.legalMoves(board)
          assertTrue(moves.nonEmpty && moves.size < 20)
    },
    test("position in check: king is actually in check") {
      FenParser.parse(checkFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(board) => assertTrue(LegalityFilter.isCheck(board))
    },
    test("pinned piece cannot move off pin line") {
      FenParser.parse(pinFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(board) =>
          val moves = LegalityFilter.legalMoves(board)
          // The knight on e4 is pinned by bishop on g6 via king on d3 — e4 knight cannot move
          val knightMoves = moves.filter(m => m.value.startsWith("e4"))
          assertTrue(knightMoves.isEmpty)
    },
    test("checkmate position has no legal moves") {
      FenParser.parse(mateFen) match
        case Left(err) => assertTrue(false) ?? err
        case Right(board) => assertTrue(LegalityFilter.legalMoves(board).isEmpty)
    },
  )
