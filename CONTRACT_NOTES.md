# Contract Notes

## Event-driven migration (Kafka) — implemented (Kafka task `03`)

Per [event-driven-architecture.md](../../maichess-knowledge-base/knowledge/architecture/event-driven-architecture.md),
this service is now a **stateless stream processor** in the match flow, alongside its surviving
query RPCs.

**Stream path (live):**
- Consumes `match.events.v1` `MoveSubmitted{fen, move_uci, position_history}`, filtered to that
  payload — every other envelope (including the validator's own output) is skipped but its offset
  is still committed.
- Runs the existing pure ZIO chess logic (`ValidatorServiceLive`) and produces back to
  `match.events.v1` either `MoveValidated{resulting_fen, game_result, position_history}` or
  `MoveRejected{move_uci, reason}` (an unparseable FEN also yields `MoveRejected` rather than
  crashing the stream).
- The `position_history` opaque repetition blob rides on the events (carried in on
  `MoveSubmitted`, returned updated on `MoveValidated`), so the validator stays stateless —
  same ownership contract as before, relayed over Kafka instead of gRPC request/response.
- Envelope: `aggregate_id` (matchId) copied through, `causation_id` = the `MoveSubmitted.event_id`,
  `sequence` = incoming + 1, `producer` = `move-validator-service`, fresh `event_id`/`occurred_at`.
- Consume→produce runs in a Kafka transaction (`rebalanceSafeCommits`) for effectively-once;
  reprocessing is safe regardless (pure/deterministic), satisfying at-least-once delivery.

**Code:** pure transform in `kafka/MoveValidationProcessor.scala` (tested to 100%); the
zio-kafka consumer/transactional-producer I/O shell in `kafka/MoveValidatorStream.scala`
(excluded from coverage/Stryker like the other live-Kafka shells). Wired into `Main` and gated by
`KAFKA_ENABLED` (`KAFKA_BOOTSTRAP` for brokers) so prod, where Kafka is not deployed, runs the
service as a pure gRPC query server.

**Keeps (synchronous gRPC, used by Analysis):** `ValidateMoveSan`, `GetLegalMovesSan`,
`ConvertSequenceToSan`, and `GetLegalMoves` (board-highlight query). These are reads/notation
helpers, not state transitions, so they remain request/response.

## Protobuf event serde — implemented (Kafka task `01`)

The event schemas are now **Protobuf**, not Avro: `maichess-api-contracts/protos/events/v1/`
(`match_events.proto`, package `maichess.events.v1` — `MoveSubmitted`/`MoveValidated`/`MoveRejected`
ride the `MatchEvent` envelope). They mirror the `events/v1/*.avsc` field-for-field; the `.avsc`
files stay in place until each topic cuts over (task `02`).

Contracts **v0.6.0** is published; `io.github.maichess:platform-protos` is pinned at `0.6.0` in
`build.sbt`. Done:

1. `src/main/scala/maichess/movevalidator/kafka/ProtobufEventSerdes.scala` — a zio-kafka `Serde`
   over the ScalaPB-generated `MatchEvent` companion (raw Protobuf bytes; the end-state encoding
   once the registry is removed in task `09`). Task `03` wires this serde into the stream
   processor (`MoveValidatorStream`).
2. `src/test/scala/maichess/movevalidator/kafka/ProtobufEventSerdesSpec.scala` — round-trips the
   match.events payloads the validator handles (MoveSubmitted, MoveValidated, MoveRejected).

### Wire encoding for `match.events.v1` — raw Protobuf, no registry framing

`match.events.v1` is a **brand-new topic with no existing producer** (the move loop is born in
proto), so the validator reads/writes **raw Protobuf bytes** — there are no registry-framed
messages on this topic to be compatible with. The README's "Confluent Protobuf serde + Schema
Registry until task `09`" convention targets the three topics that *already shipped in Avro*
(`socket.outbound`, `matchmaking.events`, `match.commands`, migrated in task `02`); it does not
apply to a greenfield topic. When the C# projector/command side land (tasks `05`/`06`), they must
use the **same raw-bytes encoding** on `match.events.v1`, not the registry-framed
`Confluent.SchemaRegistry.Serdes.Protobuf` path, or the two sides will not interoperate.

**Verified:** `sbt test` (token from `.env`) — 175 tests pass; the new transform is covered 100%.
