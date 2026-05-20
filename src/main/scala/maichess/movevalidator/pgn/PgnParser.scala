package maichess.movevalidator.pgn

object PgnParser:
  private val resultTokens: Set[String] = Set("1-0", "0-1", "1/2-1/2", "*")

  def parse(pgn: String): Either[String, PgnGame] =
    if pgn.trim.isEmpty then Left("Empty PGN input")
    else
      val lines                = pgn.split('\n').toList
      val (headerLines, rest)  = lines.span(l => l.trim.startsWith("[") || l.trim.isEmpty)
      val headers              = headerLines.flatMap(parseHeader)
      val moveSection          = rest.mkString(" ")
      val sanMoves             = extractMoves(moveSection)
      val result               = extractResult(moveSection)
      Right(PgnGame(headers, sanMoves, result))

  private[movevalidator] def parseHeader(line: String): Option[PgnHeader] =
    val trimmed = line.trim
    if trimmed.startsWith("[") && trimmed.endsWith("]") then
      val inner    = trimmed.drop(1).dropRight(1).trim
      val spaceIdx = inner.indexOf(' ')
      if spaceIdx > 0 then
        val key   = inner.substring(0, spaceIdx)
        val value = inner.substring(spaceIdx + 1).trim.stripPrefix("\"").stripSuffix("\"")
        Some(PgnHeader(key, value))
      else None
    else None

  private[movevalidator] def extractMoves(moveText: String): List[String] =
    val cleaned = removeVariations(removeComments(moveText))
    tokenize(cleaned).filterNot(isNonMove).map(cleanMove).filter(_.nonEmpty)

  private[movevalidator] def extractResult(moveText: String): String =
    val tokens = tokenize(removeVariations(removeComments(moveText)))
    tokens.filter(resultTokens.contains).reverse match
      case r :: _ => r
      case Nil    => "*"

  private[movevalidator] def removeComments(s: String): String =
    s.replaceAll("\\{[^}]*\\}", " ")

  private[movevalidator] def removeVariations(s: String): String =
    val (chars, _) = s.foldLeft((List.empty[Char], 0)) { case ((acc, depth), c) =>
      c match
        case '(' => (acc, depth + 1)
        case ')' => (acc, if depth > 0 then depth - 1 else 0)
        case _   => if depth > 0 then (acc, depth) else (c :: acc, depth)
    }
    chars.reverse.mkString

  private def tokenize(s: String): List[String] =
    s.trim.split("\\s+").toList.filter(_.nonEmpty)

  private def isNonMove(token: String): Boolean =
    token.matches("\\d+\\.+") ||
    resultTokens.contains(token) ||
    token.startsWith("$")

  private def cleanMove(token: String): String =
    token.replaceAll("[!?]+$", "")
