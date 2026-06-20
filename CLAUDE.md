# CLAUDE.md — maichess-move-validator-service

## Service responsibility

Validates chess moves and enumerates legal moves for a given position. The sole authority for all
chess notation and board logic in the platform.

Exposes four RPCs:
- **`ValidateMove`** / **`GetLegalMoves`** — UCI variants, called by Match Manager
- **`ValidateMoveSan`** / **`GetLegalMovesSan`** — SAN variants, called by Analysis Service

All four share the same internal board logic. SAN generation includes piece disambiguation,
check/checkmate suffixes, castling, en passant, and promotion notation.

See `maichess-knowledge-base/maichess-structure.md` for architecture context.

## API contract

The sole source of truth is:

```
maichess-api-contracts/protos/move-validator-service/v1/moves.proto
```

Service: `maichess.move_validator.v1.Moves`  
RPCs: `ValidateMove`, `ValidateMoveSan`, `GetLegalMoves`, `GetLegalMovesSan`

Read the proto before touching any service logic. Do not infer the contract from implementation code.

If the contract cannot be implemented as specified, document the blocker in `CONTRACT_NOTES.md` at the service root. Do not implement an adjusted version until explicitly told to proceed.

## Stack

- **Scala 3** with **ZIO 2** — all effects must be modelled as `ZIO[R, E, A]`; no `Future`, no `Try` as effect boundaries
- **ZIO gRPC** (`scalapb-zio-grpc`) for the gRPC server
- **ScalaPB** for protobuf code generation (generated stubs are consumed, not owned here)
- **munit** + **zio-test** for tests (prefer `zio-test` for ZIO-native specs)
- **sbt** as build tool

## Commands

```bash
sbt compile          # Compile
sbt test             # Run all tests
sbt "testOnly *Foo"  # Run a single suite
sbt scalafix         # Linter / rewriter
sbt run              # Start the gRPC server (entry point wired via ZIOAppDefault)
```

## Module / layer structure

Follow the ZIO layer pattern — every dependency is a ZIO `ZLayer`:

```
Domain logic (pure)
  └─ Chess rules (FEN/UCI parsing, move generation, win detection)
       └─ Validator service (ZIO layer wrapping domain logic)
            └─ gRPC handler (ZIO gRPC service impl, delegates to validator layer)
                 └─ Server (ZIOAppDefault, wires layers and starts the server)
```

No business logic in the gRPC handler — it translates proto types to/from domain types only.

## Code style

- Functional programming throughout — immutable data, pure functions.
- Max ~15–20 lines per function. If you need "and" to describe it, split it.
- No comments unless explaining a non-obvious algorithm. Names carry intent.
- Scan for duplicated logic before finishing; extract it.
- ZIO-idiomatic: prefer `ZIO.succeed`, `ZIO.fail`, `ZIO.attempt` over throwing exceptions; use `ZLayer.derive` / `ZLayer.fromFunction` for wiring.

## Linting & coverage

- **WartRemover** (`Warts.unsafe` as errors) applies to all logic modules (domain, validator layer, gRPC handler).
- **100% statement coverage** is mandatory for all logic-containing code (domain logic, validator service layer, gRPC handler). Disabled only on the server entry-point wiring.
- Every change to logic code must be accompanied by tests maintaining 100% coverage.

## Tests

- Test files live in `src/test/scala/`.
- Use **zio-test** (`ZIOSpecDefault`) for ZIO-native specs.
- Do not change tests to make them pass — only change tests when the requirement they cover changes.
- Unit-test domain logic (FEN parsing, move generation, win detection) without a running server.
- Integration-test the gRPC handler via the ZIO gRPC test harness, not a live network socket.

### Mutation testing

Stryker4s is wired up as an sbt plugin. Config lives in `stryker4s.conf`
(`Main.scala` and `kafka/MoveValidatorStream.scala` excluded). Run with
`sbt stryker` from the service root. See `README.md` for details.

#### Known surviving mutants (equivalent / unreachable — do **not** "fix")

The suite kills every behaviourally-distinguishable mutant (current score
~97.8% total / ~98.0% of covered code). The mutants below survive because they
are **equivalent** — the mutation cannot change observable behaviour for any
*reachable* input — or sit on **dead defensive branches**. Killing them would
require asserting impossible/undefined behaviour or refactoring chess logic
purely to game the score, so they are left intentionally.

- **Castle file comparisons `>` ⇄ `>=`** — `MoveApplicator.scala:87` (`kingSide`),
  `SanNotation.scala:8` (`to.file > 4`), `LegalityFilter.scala:35` (castle
  pass-square). A castle's destination is always the c- or g-file and its origin
  the e-file, so the operands are never equal.
- **`PgnParser.scala:22` `spaceIdx > 0` ⇄ `>=`** — `inner` is trimmed before
  `indexOf(' ')`, so the index is never `0`.
- **Dead `fold(false)` fall-throughs `false` ⇄ `true`** — `LegalityFilter.scala:36`
  and `MoveGenerator.scala:101` only fold over hard-coded valid squares, so the
  `None` branch never executes.
- **`SanNotation.scala:34` `exists` ⇄ `forall`** — the source square in `disambig`
  always holds a piece (it comes from the legal-move list).
- **WinConditionDetector bishop-pair logic** — `:27` (`Bishop && Bishop`), `:35`
  (`b1.isDefined && b2.isDefined`), `:39` (`c == color`), `:40` (parity `% 2 == 0`).
  The redundant type/defined guards and the symmetric / consistently-flipped
  comparisons cannot change the `b1 == b2` outcome.
- **`ValidatorServiceLive.scala:62` (NoCoverage)** — the `"Cannot parse move"`
  branch is unreachable: a move that passes `isLegal` always has a parseable UCI.

When auditing mutation results, treat the above as the expected baseline; any
**new** survivor outside this list is a genuine test gap.
