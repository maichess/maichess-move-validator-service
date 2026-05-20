package maichess.movevalidator.stream

import zio.*
import zio.stream.*
import maichess.movevalidator.pgn.*
import maichess.movevalidator.rules.*

object PgnStream:

  private val defaultStartFen: String =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  // ── Source helper ─────────────────────────────────────────────────────────
  /** Creates a source stream from a sequence of PGN game strings. */
  def source(pgns: String*): ZStream[Any, Nothing, String] =
    ZStream.fromIterable(pgns)

  // ── Flow 1: Parse PGN text (external DSL) ─────────────────────────────────
  val parsePgn: ZPipeline[Any, String, String, PgnGame] =
    ZPipeline.mapZIO(pgn => ZIO.fromEither(PgnParser.parse(pgn)))

  // ── Flow 2: Validate moves and enrich with UCI + FEN ──────────────────────
  val validateMoves: ZPipeline[Any, String, PgnGame, ProcessedGame] =
    ZPipeline.mapZIO(processGame)

  // ── Sink A: Collect all processed games ───────────────────────────────────
  val collectGames: ZSink[Any, String, ProcessedGame, ProcessedGame, Chunk[ProcessedGame]] =
    ZSink.collectAll

  // ── Sink B: Summarize games (count, total moves, results) ─────────────────
  val summarize: ZSink[Any, Nothing, ProcessedGame, Nothing, PgnSummary] =
    ZSink.foldLeft(PgnSummary(0, 0, Map.empty)) { (summary, game) =>
      val count = summary.resultCounts.getOrElse(game.result, 0)
      PgnSummary(
        gameCount    = summary.gameCount + 1,
        totalMoves   = summary.totalMoves + game.moves.size,
        resultCounts = summary.resultCounts.updated(game.result, count + 1),
      )
    }

  // ── Full pipeline: PGN strings → parse → validate → collect ───────────────
  def processGames(pgns: ZStream[Any, String, String]): IO[String, Chunk[ProcessedGame]] =
    pgns.via(parsePgn).via(validateMoves).run(collectGames)

  // ── Full pipeline from lines → assemble → parse → validate → collect ──────
  def processLines(lines: ZStream[Any, Nothing, String]): IO[String, Chunk[ProcessedGame]] =
    assembleFromLines(lines).via(parsePgn).via(validateMoves).run(collectGames)

  // ── Full pipeline with summary sink ───────────────────────────────────────
  def summarizeGames(pgns: ZStream[Any, String, String]): IO[String, PgnSummary] =
    pgns.via(parsePgn).via(validateMoves).run(summarize)

  // ── Assemble raw PGN file lines into game strings ─────────────────────────
  def assembleFromLines(lines: ZStream[Any, Nothing, String]): ZStream[Any, Nothing, String] =
    (lines ++ ZStream(""))
      .mapAccum(List.empty[String]) { (acc, line) =>
        if line.trim.isEmpty then
          val hasMovetext = acc.exists(l => l.trim.nonEmpty && !l.trim.startsWith("["))
          if hasMovetext then (Nil, Some(acc.reverse.mkString("\n")))
          else (acc, None)
        else (line :: acc, None)
      }
      .collect { case Some(g) => g }

  private def processGame(game: PgnGame): IO[String, ProcessedGame] =
    val startFen = game.headers.find(_.key == "FEN").map(_.value).getOrElse(defaultStartFen)
    ZIO.fromEither(FenParser.parse(startFen)).flatMap { startBoard =>
      ZIO.foldLeft(game.sanMoves.zipWithIndex)((startBoard, List.empty[ProcessedMove])) {
        case ((board, acc), (san, idx)) =>
          val legal = LegalityFilter.legalChessMoves(board)
          SanNotation.fromSan(board, san, legal) match
            case None =>
              ZIO.fail(s"Illegal move at index $idx: $san")
            case Some(chess) =>
              val uci   = MoveGenerator.toUci(chess).value
              val next  = MoveApplicator(board, chess)
              val fen   = FenSerializer.serialize(next)
              val color = if board.sideToMove == Color.White then "w" else "b"
              ZIO.succeed((next, acc :+ ProcessedMove(board.fullMoveNumber, color, san, uci, fen)))
      }.map { case (_, moves) => ProcessedGame(game.headers, moves, game.result) }
    }
