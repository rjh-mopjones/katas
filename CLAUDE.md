# Katas — repo conventions

Multi-language kata repo for interview prep. This file is the authoring guide; follow it when
adding or changing katas so every language and kata stays consistent.

## The model (applies to every language)

Each `<lang>-katas/` has two mirrored trees:

- **`solution/`** — full reference implementation **plus tests**. The answer key. Must stay GREEN.
- **`practice/`** — the same packages, but the system-under-test (SUT) classes are blank skeletons
  the learner fills in from scratch. **No tests** — the learner writes their own.

Rules that hold across languages:

- **The per-kata README is the prompt; the learner writes their own tests.** Practice ships no
  tests on purpose — designing tests is part of the exercise. The `solution/` tests are a reference
  to compare against afterwards, not a given.
- **Practice SUT classes contain only the public API surface** (signatures that throw / are
  unimplemented) — no fields, no helper bodies, no explanatory comments. The learner designs the
  internals.
- **Fixture / domain types are real in practice** (interfaces, records, enums, value types, custom
  exceptions are copied verbatim from `solution/`) so the practice module still compiles.
- **Same relative path in both trees.** The `solution/` twin is the answer key and the source for
  regenerating a practice skeleton.

---

## Java (`java-katas/`)

- **JDK 21 required** (`pom.xml` sets `maven.compiler.release=21`). The default `mvn` JDK may be
  older (e.g. Corretto 17) and will fail to compile. Set it first:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  ```
- **Maven multi-module:** parent `pom.xml` aggregates `solution` and `practice`.
  ```bash
  mvn -pl solution test     # reference suite — green
  mvn -pl practice test      # runs the tests YOU write under practice/src/test
  mvn test                   # everything
  ```
- **Package root:** `org.kata.<kata>`.
- **Tests** (the reference suite in `solution/`, and ones you write in `practice/`): JUnit Jupiter
  5.11.3 ONLY — no Mockito, no AssertJ. Use `org.junit.jupiter.api.Test` + `Assertions.*`.
  Descriptive `snake_case` names. Concurrency tests use a `CountDownLatch` gate + done with
  `Executors.newVirtualThreadPerTaskExecutor()`.
- **Time:** time-dependent logic takes an injectable `java.util.function.LongSupplier` nanos clock
  (default `System::nanoTime`); tests drive it with an `AtomicLong`. Never use
  `System.currentTimeMillis()` for elapsed-time math.
- **Solution Javadoc is interview-grade:** explain the *why*, trade-offs, and named alternatives —
  match the depth of existing classes (e.g. `TokenBucketRateLimiter`, `ConcurrentAccountService`).

### Recipe: add a new Java kata

1. Choose a package `org.kata.<name>`.
2. In **`solution/src/main`**: write the interface(s), immutable domain types (records/enums), and
   the SUT implementation(s) with rich Javadoc. In **`solution/src/test`**: write behaviour tests
   that fully pin the contract.
3. `mvn -pl solution test` → green.
4. Mirror into **`practice/`** (no tests):
   - copy the fixture/domain types (interfaces, records, enums, exceptions) verbatim;
   - for each SUT class, write a **bare skeleton**: `package` + existing `import`s + the class
     declaration + every non-`private` constructor/method signature with body
     `throw new UnsupportedOperationException();`. Delete Javadoc, fields, private methods, and
     private nested types (keep a public nested type only if a kept signature needs it).
5. Add `practice/src/main/java/org/kata/<name>/README.md`: problem, requirements, *what you
   implement* (the public contract only), the real challenge, the run note (write your own tests),
   primer pointers.
6. Verify: `mvn -pl practice test-compile` succeeds (the skeletons + fixtures compile).

---

## C# (`csharp-katas/`)

- **.NET 8 SDK required** (`global.json` pins 8.0.x with `rollForward: latestFeature`).
- **Four projects** (the two-sided model, since .NET keeps tests in their own project):
  `solution/` (lib) + `solution.tests/` (xUnit reference suite, the answer key) and
  `practice/` (lib of blank skeletons) + `practice.tests/` (empty — the learner writes here).
  ```bash
  dotnet test solution.tests    # reference suite — green
  dotnet test practice.tests    # runs the tests YOU write
  dotnet build                  # whole solution
  dotnet test practice.tests --filter "FullyQualifiedName~Cache"   # one kata
  ```
- **Namespaces:** root `Katas`; per-kata `Katas.<Kata>`. File-scoped namespaces, one public type
  per file, PascalCase. `solution/` and `practice/` share namespaces but are separate assemblies.
- **Shared config** lives in `Directory.Build.props` (net8.0, `Nullable enable`, `ImplicitUsings`,
  and a global `using System.Collections.Concurrent`). The two lib projects set
  `TreatWarningsAsErrors=true`, so solution/practice code must be **warning-clean** (annotate
  nullability; add a discard arm to non-exhaustive `switch` expressions).
- **Tests:** xUnit (`[Fact]`/`[Theory]`, `Method_Should_Behaviour` names). No Moq/FluentAssertions.
- **Time:** time-dependent types take a `TimeProvider` ctor param (default `TimeProvider.System`);
  use `GetTimestamp`/`GetElapsedTime` or `Task.Delay(delay, timeProvider, ct)` /
  `timeProvider.CreateTimer(...)`. Tests use `FakeTimeProvider`
  (`Microsoft.Extensions.Time.Testing`, the 8.x package) and `fake.Advance(...)` — never real sleeps.
  Pattern for delayed async: start the op without awaiting, `fake.Advance(...)`, then await.
- **Skeleton bodies:** `throw new NotImplementedException();`.
- **Solution XML docs are interview-grade** (`/// <summary>`/`<remarks>` — the *why*, trade-offs,
  alternatives), matching the depth of e.g. `TokenBucketRateLimiter`, `LockFreeStack`.

### Recipe: add a new C# kata

1. Choose namespace `Katas.<Name>`.
2. In **`solution/<Name>/`** write the implementation(s) + fixtures with rich XML docs; in
   **`solution.tests/<Name>/`** write xUnit tests pinning the contract. `dotnet test solution.tests` → green.
3. Mirror into **`practice/<Name>/`** (no tests): copy fixture/domain types (interfaces, records,
   enums, exceptions) verbatim; reduce each SUT class to a **bare skeleton** (package + usings +
   class decl + non-`private` signatures throwing `NotImplementedException`; drop fields, private
   members, XML docs). Keep a public nested type only if a kept signature needs it.
4. Add `practice/<Name>/README.md` (problem, requirements, what-you-implement = public contract,
   the real challenge, run note, a Microsoft Docs link for the feature).
5. Verify: `dotnet build practice` compiles.

> **Watch out:** a non-generic SUT whose name equals its namespace leaf (e.g. `CircuitBreaker` in
> `Katas.CircuitBreaker`) collides in test files — alias the type (`using Breaker = …`) since a
> nested namespace shadows a same-named using-alias.

---

## PostgreSQL (`postgres-katas/`)

A **query** module: the learner writes a SQL query, an auto-grader checks the result. So it diverges
from the code modules — there are no "write your own tests"; the shared grader (`checker/`) is the
analog of the reference tests, and `solution/` is the answer key.

- **DB:** `postgres:17` via Docker Compose on host **5433** (never collides with a local 5432).
  `make up` starts it and loads `db/01_schema.sql` then `db/02_seed.sql`; `make reset` rebuilds.
- **Grader:** Python `pytest` + `psycopg` in `checker/` (own venv: `make venv`). `test_katas.py` is
  parametrized over kata dirs; for each it runs the learner's `practice/<kata>/query.sql` and the
  `solution/<kata>/query.sql` **live** in a rolled-back transaction and compares result sets
  (NULL/Decimal/float/JSON normalized; unordered multiset by default). Run: `make check` /
  `make check-kata KATA=04_ranking`.
- **One shared dataset** in `db/`. **Determinism is critical:** fixed anchor dates +
  `generate_series` (never `random()` or `now()`/`CURRENT_DATE`), `NUMERIC` money, `C.UTF-8` locale.
  A non-deterministic seed makes set/ordered comparisons flaky.
- **Per-kata layout:** `solution/NN_name/query.sql` (reference, with directives) ↔
  `practice/NN_name/query.sql` (blank `-- TODO`) + `practice/NN_name/README.md` (the problem).
- **Solution directives** (leading `-- key: value` comments, read by the grader):
  `-- grade: ordered` (else unordered); `-- mode: mutation` (script mutates then SELECTs final
  state; rolled back); `-- mode: concurrency` (bespoke two-connection test, e.g. SKIP LOCKED);
  `-- assert: index=<name>` / `-- assert: no-seq-scan` (EXPLAIN plan-shape checks).
- Plan-shape assertions are only reliable on selective predicates over large-enough tables (e.g.
  the GIN kata over 50k events, the index kata over a unique-ish 10k-row predicate) — never assert
  wall-clock time.

### Recipe: add a new SQL kata

1. Pick `NN_name`. Write `solution/NN_name/query.sql` with directives; run it via
   `psql -h localhost -p 5433 -U kata -d katas -f …` and confirm a sensible, deterministic result
   (ordered katas need a total `ORDER BY` with a unique tiebreaker).
2. Write `practice/NN_name/query.sql` = `-- TODO: write your query` and `practice/NN_name/README.md`
   (problem → requirements → what you write → the real challenge → run → reference).
3. Gate: temporarily `cp solution/NN_name/query.sql practice/NN_name/query.sql`, `make check-kata`
   → green; then restore the `-- TODO` blank → RED.

---

## Go (`go-katas/`)

A **concurrency-correctness** module, themed as a low-latency sports-betting trading platform.
Same two-tree model, but Go keeps tests next to the code, so the split is by module:

- **Go 1.22+, standard library ONLY** (no third-party deps; `golang.org/x/sync` only if truly
  needed). Bugs are logic/concurrency defects — everything must `go build` and `go vet` clean.
- **Two modules:** `solution/` (module `…/go-katas/solution`) and `practice/`
  (`…/go-katas/practice`). Each kata is a package at the **same relative path** in both
  (`solution/pricecache/` ↔ `practice/pricecache/`).
  ```bash
  cd go-katas/solution && go test -race ./...   # reference suite — green & race-clean
  cd go-katas/practice && go test -race ./...    # runs the tests YOU write
  # a Makefile in go-katas/ wraps these (make practice-race, make solution-race, …)
  ```
- **Package names** are lower-case domain nouns (`pricecache`, `betmachine`, `venuefanin`) — no
  `kataNN_` prefixes; the numbered index lives in `go-katas/README.md`.
- **Tests** (reference suite in `solution/`, and the ones you write in `practice/`): the stdlib
  `testing` package ONLY — no testify. Descriptive `Test_snake_or_Camel` names. Concurrency tests
  use a `close(start)` gate + `sync.WaitGroup` join to maximise contention; **deterministic, no
  `time.Sleep` for synchronisation** (a bounded poll of `runtime.NumGoroutine` for leak checks is OK).
- **Time / cancellation:** prefer `context.Context` (tests drive it with `context.WithCancel` /
  `WithTimeout`) over wall-clock. Inject a `func() time.Time` only where elapsed-time math is the point.
- **Skeleton bodies:** `panic("TODO: implement")`.
- **Solution doc comments are interview-grade** — explain the *why*, the failure mode in real-money
  terms, the trade-off, and named alternatives (match the depth of e.g. `pricecache`, `betmachine`).

### Recipe: add a new Go kata

1. Choose a package `org`-free lower-case name (`<name>`).
2. In **`solution/<name>/`**: write `<name>.go` (domain types + the SUT with rich doc comments) and
   `<name>_test.go` (behaviour tests pinning the contract, including a `-race` concurrency test).
   `cd go-katas/solution && go vet ./<name>/ && go test -race ./<name>/` → green.
3. Mirror into **`practice/<name>/`** (no tests): copy domain/fixture types (structs, enums,
   `error` vars, func types) **verbatim** so it compiles; reduce each SUT type to a **bare
   skeleton** — `type Foo struct{}` + every exported constructor/method signature with body
   `panic("TODO: implement")`. Drop fields, unexported helpers, and doc comments. Import only what
   the kept signatures need (an unused import fails the build).
4. Add `practice/<name>/README.md`: `# Title` + one-line `>` scenario → `## The problem` →
   `## Requirements` → `## What you implement` (public API only) → `## The real challenge`
   (the concurrency trap + money angle) → `## Run` (no tests, write your own;
   `cd go-katas/practice && go test -race ./<name>/`) → `## Reference` (`solution/<name>/` + the
   extension task).
5. Verify: `cd go-katas/practice && go vet ./<name>/ && go build ./<name>/` succeeds; add a row to
   `go-katas/README.md`.

---

## Commits

- **Never add `Co-Authored-By` / Claude authorship** to commits.
- Conventional, concise messages. Keep tidying separate from behaviour changes.
- Remote: `https://github.com/rjh-mopjones/katas` (git root is this directory).

## Known gotchas

- Default `mvn` JDK may be 17 → set `JAVA_HOME` to 21.
- `solution`'s `InMemoryBookingServiceRaceTest` is intentionally flaky — it asserts a data race
  *manifests*, which a warm JVM sometimes hides. Re-run if it fails in isolation; not a regression.
- A reference interview-topic source ("Java Interview Primer") drives which katas exist; new katas
  should map to a real interview topic and carry that pointer in their README.
- `go-katas/`: `go.mod` pins `go 1.22` (the locally installed toolchain). `go test ./...` over the
  `practice/` module prints `no test files` per kata — expected (the learner writes them). The
  `solution/` race tests run real goroutine contention; re-run if a `NumGoroutine` leak poll is
  ever tight on a loaded machine.
