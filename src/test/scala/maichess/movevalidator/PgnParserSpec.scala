package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.pgn.{PgnGame, PgnHeader, PgnParser}

object PgnParserSpec extends ZIOSpecDefault:

  def spec = suite("PgnParserSpec")(

    // ── parse ─────────────────────────────────────────────────────────────────
    test("parse empty input returns Left") {
      assertTrue(PgnParser.parse("").isLeft, PgnParser.parse("   ").isLeft)
    },
    test("parse valid PGN with headers and moves") {
      val pgn =
        """[Event "Test"]
          |[White "Alice"]
          |
          |1. e4 e5 2. Nf3 1-0""".stripMargin
      PgnParser.parse(pgn) match
        case Right(game) =>
          assertTrue(
            game.headers == List(PgnHeader("Event", "Test"), PgnHeader("White", "Alice")),
            game.sanMoves == List("e4", "e5", "Nf3"),
            game.result == "1-0",
          )
        case Left(err) => assertTrue(false) ?? err
    },
    test("parse PGN without headers") {
      PgnParser.parse("1. d4 d5 0-1") match
        case Right(game) =>
          assertTrue(
            game.headers.isEmpty,
            game.sanMoves == List("d4", "d5"),
            game.result == "0-1",
          )
        case Left(err) => assertTrue(false) ?? err
    },
    test("parse PGN with headers only returns empty moves") {
      PgnParser.parse("[Event \"Test\"]") match
        case Right(game) =>
          assertTrue(game.headers.size == 1, game.sanMoves.isEmpty, game.result == "*")
        case Left(err) => assertTrue(false) ?? err
    },
    test("malformed header without space is skipped") {
      val pgn =
        """[Event "Test"]
          |[NoValue]
          |
          |1. e4 1-0""".stripMargin
      PgnParser.parse(pgn) match
        case Right(game) =>
          assertTrue(game.headers == List(PgnHeader("Event", "Test")))
        case Left(err) => assertTrue(false) ?? err
    },
    test("non-bracket line in header area is skipped") {
      // empty lines pass the span predicate but parseHeader returns None
      val pgn =
        """[Event "Test"]
          |
          |1. e4 1-0""".stripMargin
      PgnParser.parse(pgn) match
        case Right(game) =>
          assertTrue(game.headers == List(PgnHeader("Event", "Test")))
        case Left(err) => assertTrue(false) ?? err
    },

    // ── comments and variations ──────────────────────────────────────────────
    test("comments are removed from movetext") {
      PgnParser.parse("1. e4 {best move} e5 1-0") match
        case Right(game) =>
          assertTrue(game.sanMoves == List("e4", "e5"))
        case Left(err) => assertTrue(false) ?? err
    },
    test("variations are removed from movetext") {
      PgnParser.parse("1. e4 (1. d4 d5) e5 1-0") match
        case Right(game) =>
          assertTrue(game.sanMoves == List("e4", "e5"))
        case Left(err) => assertTrue(false) ?? err
    },
    test("nested variations are removed") {
      PgnParser.parse("1. e4 (1. d4 (1. c4 c5) d5) e5 *") match
        case Right(game) =>
          assertTrue(game.sanMoves == List("e4", "e5"))
        case Left(err) => assertTrue(false) ?? err
    },
    test("stray closing paren at depth zero is dropped") {
      PgnParser.parse("1. e4) e5 1-0") match
        case Right(game) =>
          assertTrue(game.sanMoves == List("e4", "e5"))
        case Left(err) => assertTrue(false) ?? err
    },

    // ── annotations and NAGs ─────────────────────────────────────────────────
    test("NAG annotations are filtered out") {
      PgnParser.parse("1. e4 $1 e5 $2 1-0") match
        case Right(game) =>
          assertTrue(game.sanMoves == List("e4", "e5"))
        case Left(err) => assertTrue(false) ?? err
    },
    test("suffix annotations ! and ? are stripped from moves") {
      PgnParser.parse("1. e4! e5? 2. Nf3!! Nc6?! 1-0") match
        case Right(game) =>
          assertTrue(game.sanMoves == List("e4", "e5", "Nf3", "Nc6"))
        case Left(err) => assertTrue(false) ?? err
    },

    // ── result extraction ────────────────────────────────────────────────────
    test("all result types are extracted correctly") {
      assertTrue(
        PgnParser.parse("1. e4 1-0").toOption.get.result == "1-0",
        PgnParser.parse("1. e4 0-1").toOption.get.result == "0-1",
        PgnParser.parse("1. e4 1/2-1/2").toOption.get.result == "1/2-1/2",
        PgnParser.parse("1. e4 *").toOption.get.result == "*",
      )
    },
    test("missing result defaults to *") {
      PgnParser.parse("1. e4 e5") match
        case Right(game) => assertTrue(game.result == "*")
        case Left(err) => assertTrue(false) ?? err
    },

    // ── parseHeader edge cases ───────────────────────────────────────────────
    test("parseHeader with valid input") {
      assertTrue(
        PgnParser.parseHeader("[Event \"Test\"]") == Some(PgnHeader("Event", "Test")),
      )
    },
    test("parseHeader with no closing bracket returns None") {
      assertTrue(PgnParser.parseHeader("[Broken") == None)
    },
    test("parseHeader with no space returns None") {
      assertTrue(PgnParser.parseHeader("[NoValue]") == None)
    },
    test("parseHeader with non-bracket line returns None") {
      assertTrue(PgnParser.parseHeader("just text") == None)
    },

    // ── extractMoves / extractResult directly ────────────────────────────────
    test("extractMoves with empty text returns empty list") {
      assertTrue(PgnParser.extractMoves("") == Nil)
    },
    test("extractResult with no result returns *") {
      assertTrue(PgnParser.extractResult("e4 e5") == "*")
    },
    test("removeComments strips brace-delimited text") {
      assertTrue(PgnParser.removeComments("e4 {great} e5").contains("e4"))
      assertTrue(!PgnParser.removeComments("e4 {great} e5").contains("great"))
    },
    test("removeVariations strips parenthesized text") {
      assertTrue(PgnParser.removeVariations("e4 (d4 d5) e5") == "e4  e5")
    },
  )
