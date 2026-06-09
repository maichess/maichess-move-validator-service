package maichess.movevalidator.kafka

import maichess.events.v1.match_events.MatchEvent
import maichess.movevalidator.service.ValidatorService
import org.apache.kafka.clients.producer.ProducerRecord
import zio.*
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, OffsetBatch, Subscription}
import zio.kafka.producer.{TransactionalProducer, TransactionalProducerSettings}
import zio.kafka.serde.Serde
import zio.stream.ZStream

// I/O wiring for the move-validator stream processor. Excluded from coverage and
// mutation, like the other live-Kafka shells in the platform: the decision logic
// lives in the pure, fully-tested MoveValidationProcessor; this file only moves
// bytes. It consumes match.events.v1, runs the processor on each MoveSubmitted,
// and produces the resulting MoveValidated / MoveRejected back to match.events.v1
// inside a single Kafka transaction (consume->produce, effectively-once). Records
// the validator ignores still have their offset committed within that
// transaction so the consumer advances without re-reading them.
object MoveValidatorStream:

  val Topic   = "match.events.v1"
  val GroupId = "move-validator"

  private val valueSerde: Serde[Any, MatchEvent] = ProtobufEventSerdes.serde(MatchEvent)

  def run(bootstrap: List[String]): ZIO[ValidatorService, Throwable, Unit] =
    ZIO.scoped(make(bootstrap).flatMap(_.runDrain))

  private def make(
      bootstrap: List[String],
  ): ZIO[ValidatorService & Scope, Throwable, ZStream[Any, Throwable, Unit]] =
    for
      validator <- ZIO.service[ValidatorService]
      consumer  <- Consumer.make(consumerSettings(bootstrap))
      producer  <- TransactionalProducer.make(producerSettings(bootstrap))
    yield stream(consumer, producer, MoveValidationProcessor(validator))

  private def consumerSettings(bootstrap: List[String]): ConsumerSettings =
    ConsumerSettings(bootstrap)
      .withGroupId(GroupId)
      .withRebalanceSafeCommits(true)
      .withMaxRebalanceDuration(30.seconds)

  private def producerSettings(bootstrap: List[String]): TransactionalProducerSettings =
    TransactionalProducerSettings(bootstrap, s"$GroupId-${java.util.UUID.randomUUID()}")

  private def stream(
      consumer: Consumer,
      producer: TransactionalProducer,
      processor: MoveValidationProcessor,
  ): ZStream[Any, Throwable, Unit] =
    consumer
      .plainStream(Subscription.topics(Topic), Serde.string, valueSerde)
      .mapChunksZIO(transact(producer, processor))

  private def transact(producer: TransactionalProducer, processor: MoveValidationProcessor)(
      chunk: Chunk[CommittableRecord[String, MatchEvent]],
  ): ZIO[Any, Throwable, Chunk[Unit]] =
    ZIO.scoped {
      for
        outputs <- ZIO.foreach(chunk)(toOutput(processor))
        tx      <- producer.createTransaction
        _       <- tx.produceChunkBatch(outputs.flatten, Serde.string, valueSerde, OffsetBatch(chunk.map(_.offset)))
      yield Chunk.empty[Unit]
    }.uninterruptible

  private def toOutput(processor: MoveValidationProcessor)(
      record: CommittableRecord[String, MatchEvent],
  ): UIO[Option[ProducerRecord[String, MatchEvent]]] =
    for
      eventId    <- Random.nextUUID.map(_.toString)
      occurredAt <- Clock.instant.map(_.toEpochMilli)
      result     <- processor.handle(record.value, eventId, occurredAt)
    yield result.map(event => new ProducerRecord(Topic, record.key, event))
