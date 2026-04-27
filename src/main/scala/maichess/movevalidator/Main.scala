package maichess.movevalidator

import io.grpc.{Server, ServerBuilder, ServerInterceptor}
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import scala.concurrent.Future
import zio.{Runtime, Unsafe, ZIO, ZIOAppDefault}
import maichess.movevalidator.grpc.MovesServiceImpl
import maichess.movevalidator.service.ValidatorServiceLive
import maichess.move_validator.v1.moves.moves.{
  GetLegalMovesRequest,
  GetLegalMovesResponse,
  MovesGrpc,
  ValidateMoveRequest,
  ValidateMoveResponse,
}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object Main extends ZIOAppDefault:

  private val port: Int =
    sys.env.get("GRPC_PORT").flatMap(_.toIntOption).getOrElse(50055)

  private val otlpEndpoint: String =
    sys.env.getOrElse("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4317")

  private def buildTracerProvider(): SdkTracerProvider =
    val resource = Resource.getDefault().merge(
      Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "move-validator-service")))
    val exporter = OtlpGrpcSpanExporter.builder().setEndpoint(otlpEndpoint).build()
    SdkTracerProvider.builder()
      .setResource(resource)
      .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
      .build()

  private def buildGrpcTelemetry(tracerProvider: SdkTracerProvider): GrpcTelemetry =
    val otel = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()
    GrpcTelemetry.create(otel)

  def run =
    ZIO.acquireReleaseWith(
      ZIO.attempt(buildTracerProvider())
    )(tp => ZIO.attempt(tp.close()).orDie) { tracerProvider =>
      val grpcTelemetry = buildGrpcTelemetry(tracerProvider)
      ZIO.serviceWithZIO[MovesServiceImpl] { svc =>
        ZIO.runtime[Any].flatMap { runtime =>
          ZIO.acquireReleaseWith(
            ZIO.attempt(startServer(svc, runtime, grpcTelemetry.newServerInterceptor()))
          )(s => ZIO.attempt(s.shutdown()).orDie) { _ =>
            ZIO.logInfo(s"gRPC server listening on port $port") *> ZIO.never
          }
        }
      }.provide(MovesServiceImpl.layer, ValidatorServiceLive.layer)
    }

  private def startServer(
    svc: MovesServiceImpl,
    runtime: Runtime[Any],
    interceptor: ServerInterceptor,
  ): Server =
    ServerBuilder
      .forPort(port)
      .addService(
        MovesGrpc.bindService(
          new MovesAdapter(svc, runtime),
          scala.concurrent.ExecutionContext.global,
        )
      )
      .intercept(interceptor)
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
