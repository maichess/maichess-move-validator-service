package maichess.movevalidator

import zio.*
import zio.stream.*
import zio.test.*
import maichess.movevalidator.pgn.*
import maichess.movevalidator.stream.PgnStream

object PgnStreamSpec extends ZIOSpecDefault:

  private val foolsMate =
    """[Event "Fool's Mate"]
      |[Result "0-1"]
      |
      |1. f3 e5 2. g4 Qh4# 0-1""".stripMargin

  private val scholarsMate =
    """[Event "Scholar's Mate"]
      |[Result "1-0"]
      |
      |1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0""".stripMargin

  def spec = suite("PgnStreamSpec")(

    // ── processGames: full pipeline ──────────────────────────────────────────
    test("process a single game through the full pipeline") {
      PgnStream.processGames(ZStream(foolsMate)).map { games =>
        val game = games(0)
        assertTrue(
          games.size == 1,
          game.moves.size == 4,
          game.moves(0).san == "f3",
          game.moves(0).color == "w",
          game.moves(0).moveNumber == 1,
          game.moves(0).uci == "f2f3",
          game.moves(1).san == "e5",
          game.moves(1).color == "b",
          game.moves(3).san == "Qh4#",
          game.result == "0-1",
          game.headers == List(PgnHeader("Event", "Fool's Mate"), PgnHeader("Result", "0-1")),
        )
      }
    },
    test("process multiple games from a stream") {
      PgnStream.processGames(ZStream(foolsMate, scholarsMate)).map { games =>
        assertTrue(
          games.size == 2,
          games(0).moves.size == 4,
          games(1).moves.size == 7,
          games(1).result == "1-0",
        )
      }
    },
    test("empty PGN in stream fails with parse error") {
      PgnStream.processGames(ZStream("")).either.map { result =>
        assertTrue(result.isLeft)
      }
    },
    test("illegal move in game fails with error") {
      val badPgn = "1. e4 Nf6 2. Qh5 Qh4"  // Qh4 is not legal for black here
      PgnStream.processGames(ZStream(badPgn)).either.map { result =>
        assertTrue(result.isLeft)
      }
    },

    // ── processLines: line-based pipeline ────────────────────────────────────
    test("processLines assembles and processes a single game") {
      val lines = ZStream(
        "[Event \"Test\"]",
        "",
        "1. e4 e5 1-0",
      )
      PgnStream.processLines(lines).map { games =>
        assertTrue(
          games.size == 1,
          games(0).moves.size == 2,
          games(0).moves(0).san == "e4",
          games(0).moves(1).san == "e5",
        )
      }
    },
    test("processLines assembles multiple games from lines") {
      val lines = ZStream(
        "[Event \"G1\"]",
        "",
        "1. e4 e5 1-0",
        "",
        "[Event \"G2\"]",
        "",
        "1. d4 d5 0-1",
      )
      PgnStream.processLines(lines).map { games =>
        assertTrue(
          games.size == 2,
          games(0).moves(0).san == "e4",
          games(1).moves(0).san == "d4",
        )
      }
    },
    test("processLines with empty stream produces no games") {
      PgnStream.processLines(ZStream.empty).map { games =>
        assertTrue(games.isEmpty)
      }
    },
    test("assembleFromLines with headers only emits nothing") {
      val lines = ZStream("[Event \"Test\"]", "[Result \"*\"]")
      PgnStream.assembleFromLines(lines).runCollect.map { games =>
        assertTrue(games.isEmpty)
      }
    },
    test("assembleFromLines with movetext only emits game") {
      val lines = ZStream("1. e4 e5 1-0")
      PgnStream.assembleFromLines(lines).runCollect.map { games =>
        assertTrue(games.size == 1)
      }
    },
    test("assembleFromLines skips consecutive blank lines") {
      val lines = ZStream("", "", "1. e4 1-0")
      PgnStream.assembleFromLines(lines).runCollect.map { games =>
        assertTrue(games.size == 1)
      }
    },

    // ── summarizeGames: summary sink ─────────────────────────────────────────
    test("summarizeGames produces correct summary") {
      PgnStream.summarizeGames(ZStream(foolsMate, scholarsMate)).map { summary =>
        assertTrue(
          summary.gameCount == 2,
          summary.totalMoves == 11,
          summary.resultCounts == Map("0-1" -> 1, "1-0" -> 1),
        )
      }
    },

    // ── source helper ────────────────────────────────────────────────────────
    test("source creates stream from varargs") {
      PgnStream.source(foolsMate).runCollect.map { chunk =>
        assertTrue(chunk.size == 1)
      }
    },

    // ── custom starting FEN via header ───────────────────────────────────────
    test("game with FEN header uses custom starting position") {
      // KP vs K endgame: white pawn promotes with check
      val pgn =
        """[FEN "8/P7/8/8/8/8/8/K6k w - - 0 1"]
          |
          |1. a8=Q+ 1-0""".stripMargin
      PgnStream.processGames(ZStream(pgn)).map { games =>
        val move = games(0).moves(0)
        assertTrue(
          move.san == "a8=Q+",
          move.uci == "a7a8q",
          move.color == "w",
        )
      }
    },
    test("game with invalid FEN header fails") {
      val pgn =
        """[FEN "invalid fen data"]
          |
          |1. e4 1-0""".stripMargin
      PgnStream.processGames(ZStream(pgn)).either.map { result =>
        assertTrue(result.isLeft)
      }
    },
  )
