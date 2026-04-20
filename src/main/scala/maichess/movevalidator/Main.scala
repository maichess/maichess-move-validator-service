package maichess.movevalidator

import io.grpc.{Server, ServerBuilder}
import scala.concurrent.Future
import zio.{Runtime, Unsafe, ZIO, ZIOAppDefault}
import maichess.movevalidator.grpc.MovesServiceImpl
import maichess.movevalidator.service.ValidatorServiceLive
import maichess.movevalidator.v1.moves.{
  GetLegalMovesRequest,
  GetLegalMovesResponse,
  MovesGrpc,
  ValidateMoveRequest,
  ValidateMoveResponse,
}

object Main extends ZIOAppDefault:

  private val port: Int =
    sys.env.get("GRPC_PORT").flatMap(_.toIntOption).getOrElse(50055)

  def run =
    ZIO.serviceWithZIO[MovesServiceImpl] { svc =>
      ZIO.runtime[Any].flatMap { runtime =>
        ZIO.acquireReleaseWith(
          ZIO.attempt(startServer(svc, runtime))
        )(s => ZIO.attempt(s.shutdown()).orDie) { _ =>
          ZIO.logInfo(s"gRPC server listening on port $port") *> ZIO.never
        }
      }
    }.provide(MovesServiceImpl.layer, ValidatorServiceLive.layer)

  private def startServer(svc: MovesServiceImpl, runtime: Runtime[Any]): Server =
    ServerBuilder
      .forPort(port)
      .addService(
        MovesGrpc.bindService(
          new MovesAdapter(svc, runtime),
          scala.concurrent.ExecutionContext.global,
        )
      )
      .build()
      .start()

  private final class MovesAdapter(svc: MovesServiceImpl, runtime: Runtime[Any])
      extends MovesGrpc.Moves:

    def validateMove(request: ValidateMoveRequest): Future[ValidateMoveResponse] =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.runToFuture(svc.validateMove(request))
      }

    def getLegalMoves(request: GetLegalMovesRequest): Future[GetLegalMovesResponse] =
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.runToFuture(svc.getLegalMoves(request).mapError(_.asException()))
      }
