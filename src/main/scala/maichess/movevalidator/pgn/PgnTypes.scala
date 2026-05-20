package maichess.movevalidator.pgn

case class PgnHeader(key: String, value: String)

case class PgnGame(
  headers:  List[PgnHeader],
  sanMoves: List[String],
  result:   String,
)

case class ProcessedMove(
  moveNumber:   Int,
  color:        String,
  san:          String,
  uci:          String,
  resultingFen: String,
)

case class ProcessedGame(
  headers: List[PgnHeader],
  moves:   List[ProcessedMove],
  result:  String,
)

case class PgnSummary(
  gameCount:    Int,
  totalMoves:   Int,
  resultCounts: Map[String, Int],
)
