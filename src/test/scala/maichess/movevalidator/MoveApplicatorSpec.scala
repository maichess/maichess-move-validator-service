package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.rules.{Color, FenParser, FenSerializer, MoveApplicator, PieceType, Square}

object MoveApplicatorSpec extends ZIOSpecDefault:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  private def apply(fen: String, uci: String) =
    FenParser.parse(fen).flatMap { board =>
      MoveApplicator.fromUci(board, uci)
        .toRight(s"Cannot resolve UCI move $uci")
        .map(MoveApplicator(board, _))
    }

  def spec = suite("MoveApplicatorSpec")(
    test("pawn push updates FEN correctly") {
      apply(startFen, "e2e4") match
        case Left(err)  => assertTrue(false) ?? err
        case Right(b)   =>
          val fen = FenSerializer.serialize(b)
          assertTrue(
            fen.contains("4P3") || fen.contains("e4"),
            b.enPassantSquare.isDefined,
            b.sideToMove == Color.Black,
          )
    },
    test("en passant capture removes the captured pawn") {
      // Position: white pawn on e5, black pawn moves d7-d5 creating ep on d6
      val epFen = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3"
      apply(epFen, "e5d6") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  =>
          val d5 = Square.fromAlgebraic("d5")
          assertTrue(d5.fold(false)(sq => b.pieceAt(sq).isEmpty))
    },
    test("kingside castling moves both king and rook") {
      // Position with empty f1, g1
      val castleFen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
      apply(castleFen, "e1g1") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  =>
          val g1 = Square.fromAlgebraic("g1")
          val f1 = Square.fromAlgebraic("f1")
          val e1 = Square.fromAlgebraic("e1")
          assertTrue(
            g1.fold(false)(sq => b.pieceAt(sq).exists(_.pieceType == PieceType.King)),
            f1.fold(false)(sq => b.pieceAt(sq).exists(_.pieceType == PieceType.Rook)),
            e1.fold(false)(sq => b.pieceAt(sq).isEmpty),
          )
    },
    test("queenside castling moves both king and rook") {
      val castleFen = "r3kbnr/pppqpppp/2npb3/8/3PP3/2NB1N2/PPP2PPP/R1BQK2R b KQkq - 4 6"
      apply(castleFen, "e8c8") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  =>
          val c8 = Square.fromAlgebraic("c8")
          val d8 = Square.fromAlgebraic("d8")
          assertTrue(
            c8.fold(false)(sq => b.pieceAt(sq).exists(_.pieceType == PieceType.King)),
            d8.fold(false)(sq => b.pieceAt(sq).exists(_.pieceType == PieceType.Rook)),
          )
    },
    test("pawn promotion changes piece type") {
      val promoFen = "8/4P3/8/8/8/8/8/4K1k1 w - - 0 1"
      apply(promoFen, "e7e8q") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  =>
          val e8 = Square.fromAlgebraic("e8")
          assertTrue(e8.fold(false)(sq => b.pieceAt(sq).exists(_.pieceType == PieceType.Queen)))
    },
    test("half-move clock resets on pawn move") {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 10 6"
      apply(fen, "e2e4") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(b.halfMoveClock == 0)
    },
    test("half-move clock resets on capture") {
      val capFen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 5 2"
      apply(capFen, "e4d5") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(b.halfMoveClock == 0)
    },
    test("half-move clock increments on non-pawn non-capture move") {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 2 1"
      apply(fen, "b8c6") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(b.halfMoveClock == 3)
    },
    test("full-move number increments after Black moves") {
      apply(startFen, "e2e4") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b1) =>
          MoveApplicator.fromUci(b1, "e7e5") match
            case None     => assertTrue(false) ?? "Cannot parse e7e5"
            case Some(m2) =>
              val b2 = MoveApplicator(b1, m2)
              assertTrue(b2.fullMoveNumber == 2)
    },
    test("full-move number does not increment after White moves") {
      apply(startFen, "e2e4") match
        case Left(err) => assertTrue(false) ?? err
        case Right(b)  => assertTrue(b.fullMoveNumber == 1)
    },
  )
