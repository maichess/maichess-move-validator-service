package maichess.movevalidator

import zio.test.*
import maichess.movevalidator.pgn.{PgnHeader, PgnParser}

// Targets the PgnParser mutants the happy-path PgnParserSpec leaves alive: the exact
// empty-input message, the header bracket guards, the spaceIdx boundary, the movetext
// line-join separator, the comment-replacement separator, and the variation-depth guard.
object PgnParserMessageSpec extends ZIOSpecDefault:

  private def moves(pgn: String): List[String] =
    PgnParser.parse(pgn).fold(e => throw new RuntimeException(e), _.sanMoves)

  def spec = suite("PgnParserMessageSpec")(
    test("empty input message") {
      assertTrue(PgnParser.parse("") == Left("Empty PGN input"))
    },
    test("whitespace-only input message") {
      assertTrue(PgnParser.parse("   ") == Left("Empty PGN input"))
    },

    // ── parseHeader bracket guards (start "[" AND end "]") ────────────────────
    test("a line ending in ] but not starting with [ is not a header") {
      assertTrue(PgnParser.parseHeader("Event \"X\"]") == None)
    },
    test("a line starting with [ but not ending in ] is not a header") {
      assertTrue(PgnParser.parseHeader("[Event X") == None)
    },
    test("a well-formed header still parses") {
      assertTrue(PgnParser.parseHeader("[Event \"X\"]") == Some(PgnHeader("Event", "X")))
    },
    test("header with a leading space (empty key) is rejected") {
      // spaceIdx == 0 must fail the spaceIdx > 0 guard, not be treated as a key.
      assertTrue(PgnParser.parseHeader("[ value]") == None)
    },

    // ── movetext line join separator ─────────────────────────────────────────
    test("moves split across lines stay separate tokens") {
      val pgn =
        """[Event "X"]
          |
          |1. e4 e5
          |2. Nf3 Nc6 1-0""".stripMargin
      assertTrue(moves(pgn) == List("e4", "e5", "Nf3", "Nc6"))
    },

    // ── comment replacement separator ────────────────────────────────────────
    test("a comment abutting two moves does not merge them") {
      assertTrue(moves("1. e4{black replies}e5 1-0") == List("e4", "e5"))
    },

    // ── variation depth guard ────────────────────────────────────────────────
    test("a stray close-paren does not let a following variation leak through") {
      // The ')' at depth 0 must clamp to 0 (not underflow), so the later "(d4)"
      // variation is still stripped.
      assertTrue(moves("1. e4 ) (d4) e5 1-0") == List("e4", "e5"))
    },
  )
