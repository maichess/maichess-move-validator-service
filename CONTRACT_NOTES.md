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
