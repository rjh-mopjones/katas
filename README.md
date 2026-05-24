# Katas

Coding katas for interview prep, organised by language. Every kata ships **twice**: a worked
**solution** and a blank **practice** skeleton driven by the *same tests* — so you implement from
scratch and verify yourself, then diff against the reference when stuck.

## Languages

| Language | Directory | Stack | Katas |
|----------|-----------|-------|-------|
| Java | [`java-katas/`](java-katas/) | Maven · JDK 21 · JUnit 5 | 18 packages (LLD, concurrency, caching, resilience, rate limiting, async/messaging, lock-free) |

_More languages will sit alongside as `<lang>-katas/`._

## How each language is organised

Two parallel trees with identical structure:

```
<lang>-katas/
├── solution/     full reference implementations + tests  (always GREEN)
└── practice/     same structure + same tests, but the classes you implement are blank  (RED)
```

- **The tests are the spec.** Make them pass.
- **Each kata has a README** stating the problem, requirements, and the real challenge.
- Same relative path in both trees → open the twins side-by-side; the `solution/` copy is the
  answer key.

See [`java-katas/README.md`](java-katas/README.md) for the kata index and Java build commands.

## Adding a new language

Create a sibling directory `<lang>-katas/` that follows the same `solution/` + `practice/`
split, mirror the tests into both, and leave the practice implementations blank. Then add a row
to the table above. Conventions for authoring katas are in [`CLAUDE.md`](CLAUDE.md).
