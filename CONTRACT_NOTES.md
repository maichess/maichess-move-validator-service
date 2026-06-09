# Contract Notes

## Event-driven migration (Kafka) — planned

Per [event-driven-architecture.md](../../maichess-knowledge-base/event-driven-architecture.md),
this service becomes a **stateless stream processor** in the match flow. Event schemas are Avro
in `maichess-api-contracts/events/v1/`.

**Becomes:**
- Consumes `match.events.v1` `MoveSubmitted{fen, move_uci, position_history}`.
- Runs the existing pure ZIO chess logic and produces `match.events.v1`
  `MoveValidated{resulting_fen, game_result, position_history}` or `MoveRejected{reason}`.
- The `position_history` opaque repetition blob rides on the events (carried in on
  `MoveSubmitted`, returned updated on `MoveValidated`), so the validator stays stateless —
  same ownership contract as before, relayed over Kafka instead of gRPC request/response.
- Reprocessing is safe (pure/deterministic), satisfying at-least-once delivery.

**Keeps (synchronous gRPC, used by Analysis):** `ValidateMoveSan`, `GetLegalMovesSan`,
`ConvertSequenceToSan`, and `GetLegalMoves` (board-highlight query). These are reads/notation
helpers, not state transitions, so they remain request/response.

Not yet implemented in code — Phase 0 lands the ADR, Avro schemas, and Kafka infra only.

## Protobuf event serde — pending v0.6.0 publish (Kafka task `01`)

The event schemas are now **Protobuf**, not Avro: `maichess-api-contracts/protos/events/v1/`
(`match_events.proto`, package `maichess.events.v1` — `MoveSubmitted`/`MoveValidated`/`MoveRejected`
ride the `MatchEvent` envelope). They mirror the `events/v1/*.avsc` field-for-field; the `.avsc`
files stay in place until each topic cuts over (task `02`).

**Blocked on the contracts publish** (publish-first — see
[serialization-protobuf-migration.md](../../maichess-knowledge-base/knowledge/architecture/serialization-protobuf-migration.md)):

1. The user tags/pushes contracts **v0.6.0** so the generated `maichess.events.v1` types ship in
   `platform-protos`. A fresh agent shell cannot restore the just-published version.
2. Bump `io.github.maichess:platform-protos` in `build.sbt` from `0.4.0` → `0.6.0`.
3. Add a zio-kafka **Protobuf serde** over the ScalaPB-generated `MatchEvent` type — Confluent
   Protobuf serde + Schema Registry during the transition. Serde plumbing only; **no
   producer/consumer is switched in task `01`.**

Cannot compile or test until step 1–2 land.
