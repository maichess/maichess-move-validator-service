package maichess.movevalidator.grpc

import io.grpc.Status
import zio.{IO, UIO, URLayer, ZIO, ZLayer}
import maichess.movevalidator.domain.{Fen, GameResult, UciMove, ValidateSanResult, ValidationResult}
import maichess.movevalidator.service.ValidatorService
import maichess.move_validator.v1.moves.moves.{
  GameResult         => ProtoGameResult,
  ConvertSequenceToSanRequest,
  ConvertSequenceToSanResponse,
  GetLegalMovesRequest,
  GetLegalMovesResponse,
  GetLegalMovesSanRequest,
  GetLegalMovesSanResponse,
  LegalMove          => ProtoLegalMove,
  ValidateMoveRequest,
  ValidateMoveResponse,
  ValidateMoveSanRequest,
  ValidateMoveSanResponse,
}

final class MovesServiceImpl(validator: ValidatorService):

  def validateMove(req: ValidateMoveRequest): UIO[ValidateMoveResponse] =
    val history = req.positionHistory.toList
    (for
      fen    <- ZIO.fromEither(Fen.parse(req.fen)).mapError(r => ValidateMoveResponse(valid = false, reason = r))
      uci    <- ZIO.fromEither(UciMove.parse(req.move)).mapError(r => ValidateMoveResponse(valid = false, reason = r))
      result <- validator.validateMove(fen, uci, history).mapError(r => ValidateMoveResponse(valid = false, reason = r))
    yield toResponse(result)).merge

  def getLegalMoves(req: GetLegalMovesRequest): IO[Status, GetLegalMovesResponse] =
    Fen.parse(req.fen) match
      case Left(reason) =>
        ZIO.fail(Status.INVALID_ARGUMENT.withDescription(reason))
      case Right(fen) =>
        validator.legalMoves(fen)
          .mapError(r => Status.INTERNAL.withDescription(r))
          .map(moves => GetLegalMovesResponse(moves = moves.map(_.value)))

  def validateMoveSan(req: ValidateMoveSanRequest): UIO[ValidateMoveSanResponse] =
    val history = req.positionHistory.toList
    (for
      fen    <- ZIO.fromEither(Fen.parse(req.fen)).mapError(r => ValidateMoveSanResponse(valid = false, reason = r))
      result <- validator.validateMoveSan(fen, req.move, history).mapError(r => ValidateMoveSanResponse(valid = false, reason = r))
    yield toSanResponse(result)).merge

  def getLegalMovesSan(req: GetLegalMovesSanRequest): IO[Status, GetLegalMovesSanResponse] =
    Fen.parse(req.fen) match
      case Left(reason) =>
        ZIO.fail(Status.INVALID_ARGUMENT.withDescription(reason))
      case Right(fen) =>
        validator.legalMovesSan(fen)
          .mapError(r => Status.INTERNAL.withDescription(r))
          .map(moves => GetLegalMovesSanResponse(moves = moves.map(m => ProtoLegalMove(uci = m.uci, san = m.san))))

  def convertSequenceToSan(req: ConvertSequenceToSanRequest): IO[Status, ConvertSequenceToSanResponse] =
    Fen.parse(req.startingFen) match
      case Left(reason) =>
        ZIO.fail(Status.INVALID_ARGUMENT.withDescription(reason))
      case Right(fen) =>
        validator.convertSequenceToSan(fen, req.uciMoves.toList)
          .mapError(r => Status.INVALID_ARGUMENT.withDescription(r))
          .map(sanMoves => ConvertSequenceToSanResponse(sanMoves = sanMoves))

  private def toResponse(result: ValidationResult): ValidateMoveResponse = result match
    case ValidationResult.Valid(fen, gr, history) =>
      ValidateMoveResponse(
        valid = true,
        resultingFen = fen.value,
        gameResult = toProtoResult(gr),
        positionHistory = history,
      )
    case ValidationResult.Invalid(reason) =>
      ValidateMoveResponse(valid = false, reason = reason)

  private def toSanResponse(result: ValidateSanResult): ValidateMoveSanResponse = result match
    case ValidateSanResult.Valid(fen, gr, history, uci) =>
      ValidateMoveSanResponse(
        valid = true,
        resultingFen = fen.value,
        gameResult = toProtoResult(gr),
        positionHistory = history,
        uciMove = uci,
      )
    case ValidateSanResult.Invalid(reason) =>
      ValidateMoveSanResponse(valid = false, reason = reason)

  private def toProtoResult(gr: GameResult): ProtoGameResult = gr match
    case GameResult.None                 => ProtoGameResult.GAME_RESULT_NONE
    case GameResult.WhiteWon             => ProtoGameResult.GAME_RESULT_WHITE_WON
    case GameResult.BlackWon             => ProtoGameResult.GAME_RESULT_BLACK_WON
    case GameResult.Stalemate            => ProtoGameResult.GAME_RESULT_STALEMATE
    case GameResult.FiftyMoveRule        => ProtoGameResult.GAME_RESULT_FIFTY_MOVE_RULE
    case GameResult.InsufficientMaterial => ProtoGameResult.GAME_RESULT_INSUFFICIENT_MATERIAL
    case GameResult.ThreefoldRepetition  => ProtoGameResult.GAME_RESULT_THREEFOLD_REPETITION

object MovesServiceImpl:
  val layer: URLayer[ValidatorService, MovesServiceImpl] =
    ZLayer.fromFunction(new MovesServiceImpl(_))
