# maichess-move-validator-service

See `CLAUDE.md` for architecture, contracts, and design notes.

## Mutation Testing (Stryker4s)

Stryker4s is wired up as an sbt plugin (`project/plugins.sbt`). Configuration
lives in `stryker4s.conf`. Only `Main.scala` is excluded (server entry point);
all chess rule, validator, and gRPC handler code is mutated.

```bash
# Run mutation tests from the service root
sbt stryker
```

After the run, open `target/stryker4s-report-<timestamp>/index.html` in a
browser to inspect surviving mutants.

To bump the Stryker4s version: edit `project/plugins.sbt`.
