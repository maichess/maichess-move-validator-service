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

## Protobuf event serde — implemented (Kafka task `01`)

The event schemas are now **Protobuf**, not Avro: `maichess-api-contracts/protos/events/v1/`
(`match_events.proto`, package `maichess.events.v1` — `MoveSubmitted`/`MoveValidated`/`MoveRejected`
ride the `MatchEvent` envelope). They mirror the `events/v1/*.avsc` field-for-field; the `.avsc`
files stay in place until each topic cuts over (task `02`).

Contracts **v0.6.0** is published; `io.github.maichess:platform-protos` is pinned at `0.6.0` in
`build.sbt`. Done:

1. `src/main/scala/maichess/movevalidator/kafka/ProtobufEventSerdes.scala` — a zio-kafka `Serde`
   over the ScalaPB-generated `MatchEvent` companion (raw Protobuf bytes; the end-state encoding
   once the registry is removed in task `09`). Serde plumbing only; **no producer/consumer is
   wired in task `01`** — the stream processor lands in task `03`.
2. `src/test/scala/maichess/movevalidator/kafka/ProtobufEventSerdesSpec.scala` — round-trips the
   match.events payloads the validator handles (MoveSubmitted, MoveValidated, MoveRejected).

**Local verify pending (auth handoff):** a fresh agent shell has no `GITHUB_TOKEN`, so `sbt` cannot
resolve `platform-protos@0.6.0` from GitHub Packages (401). Run `sbt test` where the token is
available to confirm.
