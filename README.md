# Katas

Coding katas for interview prep, organised by language. Every kata has a worked **solution** and a
blank **practice** skeleton. You implement from scratch against the problem statement, **write your
own tests**, and diff against the reference solution when stuck.

## Languages

| Language | Directory | Stack | Katas |
|----------|-----------|-------|-------|
| Java | [`java-katas/`](java-katas/) | Maven · JDK 21 · JUnit 5 | 18 packages (LLD, concurrency, caching, resilience, rate limiting, async/messaging, lock-free) |
| C# | [`csharp-katas/`](csharp-katas/) | .NET 8 · xUnit | 23 katas (LINQ/iterators, async streams, channels, records, pattern matching, `Span<T>`, generics/variance, plus classic LLD ports) — focused on **language mastery** |
| SQL | [`postgres-katas/`](postgres-katas/) | PostgreSQL 17 (Docker) · pytest grader | 26 katas (window functions, recursive CTEs & graph traversal, JSON/JSONB + GIN, full-text/trigram, gaps-and-islands, percentiles, upsert/MERGE, SKIP LOCKED, EXPLAIN/indexing) — **write a query, auto-graded** |
| Go | [`go-katas/`](go-katas/) | Go 1.22 · `testing` · `-race` | 8 katas (data races, goroutine leaks, channel discipline, lost updates, state-machine idempotency, fan-in backpressure, context propagation, graceful shutdown) — concurrency correctness for a low-latency trading platform |

_More languages will sit alongside as `<lang>-katas/`._

## How each language is organised

Two parallel trees with identical package structure:

```
<lang>-katas/
├── solution/     full reference implementations + tests  (the answer key — always GREEN)
└── practice/     same packages, but the classes you implement are blank skeletons; NO tests
```

- **Start from the README.** Each kata package has a README stating the problem, requirements, and
  the real challenge.
- **Write your own tests.** The practice side intentionally ships *without* tests — driving your
  own tests is part of the exercise. The `solution/` twin has reference tests if you want to
  compare approaches afterwards.
- Same relative path in both trees → the `solution/` copy is the answer key (full implementation +
  tests) for any kata you're stuck on.

See [`java-katas/README.md`](java-katas/README.md) for the kata index and build commands.

## Adding a new language

Create a sibling directory `<lang>-katas/` that follows the same `solution/` + `practice/`
split — full reference (with tests) in `solution/`, blank skeletons and per-kata READMEs (no tests)
in `practice/`. Then add a row to the table above. Authoring conventions are in
[`CLAUDE.md`](CLAUDE.md).
