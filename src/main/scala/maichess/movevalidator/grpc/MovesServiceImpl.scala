package maichess.movevalidator.grpc

import io.grpc.Status
import zio.{IO, UIO, URLayer, ZIO, ZLayer}
import maichess.movevalidator.domain.{Fen, GameResult, UciMove, ValidationResult}
import maichess.movevalidator.service.ValidatorService
import maichess.move_validator.v1.moves.moves.{
  GameResult => ProtoGameResult,
  GetLegalMovesRequest,
  GetLegalMovesResponse,
  ValidateMoveRequest,
  ValidateMoveResponse,
}

final class MovesServiceImpl(validator: ValidatorService):

  def validateMove(req: ValidateMoveRequest): UIO[ValidateMoveResponse] =
    (for
      fen    <- ZIO.fromEither(Fen.parse(req.fen)).mapError(r => ValidateMoveResponse(valid = false, reason = r))
      uci    <- ZIO.fromEither(UciMove.parse(req.move)).mapError(r => ValidateMoveResponse(valid = false, reason = r))
      result <- validator.validateMove(fen, uci).mapError(r => ValidateMoveResponse(valid = false, reason = r))
    yield toResponse(result)).merge

  def getLegalMoves(req: GetLegalMovesRequest): IO[Status, GetLegalMovesResponse] =
    Fen.parse(req.fen) match
      case Left(reason) =>
        ZIO.fail(Status.INVALID_ARGUMENT.withDescription(reason))
      case Right(fen) =>
        validator.legalMoves(fen)
          .mapError(r => Status.INTERNAL.withDescription(r))
          .map(moves => GetLegalMovesResponse(moves = moves.map(_.value)))

  private def toResponse(result: ValidationResult): ValidateMoveResponse = result match
    case ValidationResult.Valid(fen, gr) =>
      ValidateMoveResponse(valid = true, resultingFen = fen.value, gameResult = toProtoResult(gr))
    case ValidationResult.Invalid(reason) =>
      ValidateMoveResponse(valid = false, reason = reason)

  private def toProtoResult(gr: GameResult): ProtoGameResult = gr match
    case GameResult.None      => ProtoGameResult.GAME_RESULT_NONE
    case GameResult.WhiteWon  => ProtoGameResult.GAME_RESULT_WHITE_WON
    case GameResult.BlackWon  => ProtoGameResult.GAME_RESULT_BLACK_WON
    case GameResult.Stalemate => ProtoGameResult.GAME_RESULT_STALEMATE
    case GameResult.Draw      => ProtoGameResult.GAME_RESULT_DRAW

object MovesServiceImpl:
  val layer: URLayer[ValidatorService, MovesServiceImpl] =
    ZLayer.fromFunction(new MovesServiceImpl(_))
