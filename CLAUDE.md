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

## C++ (`cpp-katas/`)

A **mechanics + concurrency** module, themed as a low-latency trading platform (like `go-katas/`).
Same two-tree model; C++ keeps tests next to the code, split by CMake target instead of by module.

- **C++20, CMake ≥ 3.20, standard library ONLY** (no GoogleTest/Catch2/Boost). A ~100-line
  hand-rolled harness (`solution/common/harness.hpp`) is the analogue of Go's stdlib `testing`.
  Everything must build `-Wall -Wextra` clean (solution adds `-Werror`).
- **Two roots, built by one CMake project:** `solution/` (reference impls + tests, always GREEN) and
  `practice/` (blank skeletons; a `practice_compile` OBJECT target proves they compile — the C++
  analogue of `go build ./...`). Each kata is a folder at the **same relative path** in both.
  ```bash
  cd cpp-katas
  make solution        # build all + ctest — reference suite, GREEN (also compiles the skeletons)
  make practice        # compile just the skeletons (no tests)
  make solution-tsan   # rebuild -DKATAS_SANITIZE=thread and ctest — the `go test -race` analogue
  ctest --test-dir build -R <kata>   # one kata (≈ go test ./<kata>/)
  ```
- **Namespace** `katas`; per-kata folder is a lower-case domain noun (`exchange_session`,
  `top_of_book`) — no `kataNN_` prefix; the numbered index lives in `cpp-katas/README.md`.
- **Scenario framing is mandatory** (this is what makes a kata, not a data-structure drill): name the
  SUT after a **trading-system component**, open the README with a one-line `>` scenario blockquote
  naming where it lives, tell "The problem" as a narrative, and **defer the data-structure /
  concurrency depth to a "The real challenge" section**. Do not ship a kata named after a bare
  structure (`ring_buffer`, `spsc_queue`); frame it as the component (`tick_buffer`, `feed_pipe`).
  The `java-katas` `orderbook` README is the tone template.
- **Test harness:** header-only. `KATA_TEST(name) { ... }` self-registers a test; `EXPECT_TRUE/EQ/NE`,
  `ASSERT_TRUE/EQ`, `EXPECT_THROWS(expr, Exc)`; `KATA_MAIN()` at the end of the `*_test.cpp` runs
  all registered tests (optional `argv[1]` substring filter). `EXPECT_EQ` binds operands **by value**
  (avoids dangling references from `opt.value()`), so compared types must be copyable.
- **Concurrency tests:** put extra stress cases in a second `*_stress_test.cpp` with **no**
  `KATA_MAIN()` — every TU registers into one shared registry, so the single `main` in `*_test.cpp`
  runs them all. Gate worker threads with `kata::StartGate` (a `std::latch`, the `close(start)`
  analogue) and join via `std::jthread`. Add a `Seq`-style self-consistency invariant so torn/lost
  reads are caught **even without** the sanitizer. Verify under `-DKATAS_SANITIZE=thread` (TSan).
- **Payload atomicity trap:** a seqlock's payload must be per-field `std::atomic` (relaxed), not plain
  — a plain-access seqlock is a data race (UB) that TSan flags. An SPSC ring's slots *can* be plain
  `T` because the release/acquire hand-off gives each slot a single accessor at a time.
- **Skeleton body idiom:** `throw std::logic_error("TODO: implement");`. But a `noexcept` member must
  not throw (`-Wexceptions`) — give those a benign stub (`return false;` / `return {};`). Keep every
  public signature identical to the solution; drop private members/helpers. Copy fixture/domain types
  (structs, provided scaffolding like `OrderPool`) **verbatim** so the skeleton compiles.
- **Time/clock convention** (for any future time-based kata): inject a
  `Clock = std::function<std::chrono::steady_clock::time_point()>` defaulting to
  `std::chrono::steady_clock::now`; tests drive a manual clock — never real sleeps for elapsed-time
  math.
- **Solution doc comments are interview-grade** — a file-level block explaining the trap, the chosen
  primitive, the trade-off + named alternatives, and the money angle (why the bug costs real P&L),
  matching the depth of `exchange_session.hpp` / `top_of_book.hpp`.

### Recipe: add a new C++ kata

1. **Frame it as a component.** Pick a trading-system component and a lower-case folder `<name>`; the
   README leads with a `>` scenario blockquote and defers the C++ depth to "The real challenge"
   (see `orderbook`). Never a bare data-structure name.
2. In **`solution/<name>/`**: write `<name>.hpp` (fixtures + the SUT, header-only, interview-grade doc
   comment) and `<name>_test.cpp` (behaviour tests ending in `KATA_MAIN()`); for a concurrency kata
   add `<name>_stress_test.cpp` (gated `StartGate` stress, no `KATA_MAIN()`).
3. Add a `kata_test(<name> …sources…)` line to `solution/CMakeLists.txt` (registers the CTest).
4. Mirror into **`practice/<name>/`** (no tests): copy fixtures verbatim; reduce the SUT to the same
   public signatures with `throw std::logic_error("TODO: implement")` bodies (benign stubs for
   `noexcept` members). Add `compile_check.cpp` (`#include` the header; for a template, force it with
   `template class katas::<Type><…>;`) and a `README.md` (the 8-section format). Add the
   `compile_check.cpp` to `practice/CMakeLists.txt`'s `practice_compile` sources + its include dir.
5. Verify: `make solution` is GREEN, `make practice` compiles, `make solution-tsan` is race-clean for
   a concurrency kata; add a row to `cpp-katas/README.md`.

---

## Rust (`rust-katas/`)

A **senior-interview** module: the open-ended "implement this in ~an hour" asks. **~50% trading
components, ~50% plain CS/Rust classics** (LRU, thread pool, expression evaluator, pub/sub) — a
deliberate mix (do NOT make every kata trading-themed). Same two-tree model, split by crate.

- **Edition 2024, stable Rust, standard library ONLY** (no third-party crates). Nightly + the `miri`
  component are needed ONLY for the `spscring` unsafe capstone. Everything must be `cargo clippy
  -p solution -- -D warnings` clean.
- **Cargo workspace, two library crates:** `solution/` (reference impls + tests, always GREEN) and
  `practice/` (blank skeletons; `cargo build -p practice` proves they compile — the analogue of
  `go build` / `cmake --build practice`). Each kata is a directory module at the **same relative path**
  in both: `solution/src/<kata>/mod.rs` ↔ `practice/src/<kata>/mod.rs`.
  ```bash
  cd rust-katas
  make solution        # cargo test -p solution — reference suite GREEN (also add the mod to both lib.rs)
  make practice        # cargo build -p practice — skeletons compile
  make clippy          # cargo clippy -p solution -- -D warnings
  make miri            # cargo +nightly miri test -p solution spscring (needs: rustup component add miri)
  cargo test -p solution <kata>   # one kata
  ```
- **No hand-rolled harness (unlike C++):** testing is BUILT IN — `#[test]` + `cargo test`. That is the
  "stdlib only" story. Each solution module ends in a `#[cfg(test)] mod tests`. Per-kata folder =
  directory module so the practice `README.md` sits beside `mod.rs` (the compiler ignores the `.md`).
- **Scenario framing is mandatory, theme is not:** each kata maps to a real senior "implement-in-an-
  hour" ask across three flavours — **trading** components, plain **CS/Rust classics** (a calculator,
  an LRU cache — NO forced trading dressing), and real-world **LLD scenarios** (connection pool,
  circuit breaker, parking lot, vending machine, like `java-katas`). Lead each README with a `>`
  scenario and defer the language depth to "The real challenge". Prefer a real *system* over a bare
  "implement primitive X" drill where you can (the RAII connection pool over "implement a `Drop`
  guard"). Money angle only on the trading katas.
- **Skeleton body idiom:** `todo!()`. A crate of `todo!()` bodies still compiles (it is `!`, coerces
  anywhere), so `cargo build -p practice` is the compile-gate. Copy fixture/domain types (structs,
  enums, provided scaffolding) **verbatim**; keep public signatures identical; prefix otherwise-unused
  params with `_` to keep the skeleton tidy.
- **Rust changes the concurrency game:** it eliminates **data races at compile time** (Send/Sync +
  borrow checker), so there is NO `-race`/TSan "prove the data race" exercise — a data race won't
  compile. Concurrency katas target **deadlock** (lock ordering — `positionbook`), **lost update**
  (Mutex granularity), **channel discipline / graceful shutdown** (`threadpool`), and — for `unsafe`
  code — real **UB verified with Miri** (`spscring`). Gate concurrency-stress threads with
  `std::sync::Barrier` inside `std::thread::scope` (the `close(start)` analogue); keep them
  deterministic (no `sleep` for synchronisation) with a self-consistency/FIFO invariant.
- **`unsafe` katas** carry an interview-grade `//!` doc justifying every `unsafe` block (a `// SAFETY:`
  comment per block), a hand-written `unsafe impl Send/Sync` with its justification, and a `Drop` that
  cleans up; verify under `cargo +nightly miri test`.
- **Solution `//!` docs are interview-grade** — the trap, the chosen construct, the trade-off + named
  alternatives, and (trading katas only) the money angle — matching `tickparser.rs` / `spscring.rs`.

### Recipe: add a new Rust kata

1. **Map it to a real senior ask** and pick a lower-case module name `<name>`; decide trading vs
   classic (keep the module ~50/50). The README leads with a `>` scenario and defers depth to "The
   real challenge"; never a bare-data-structure framing for a trading kata.
2. In **`solution/src/<name>/mod.rs`**: fixtures + the impl (interview-grade `//!` doc) + a
   `#[cfg(test)] mod tests`; a concurrency kata adds a `Barrier`-gated stress test.
3. Add `pub mod <name>;` to **both** `solution/src/lib.rs` and `practice/src/lib.rs`.
4. Mirror into **`practice/src/<name>/mod.rs`** (no tests): fixtures verbatim + the same public
   signatures with `todo!()` bodies; add a `README.md` (the 8-section format).
5. Verify: `make solution` GREEN, `make practice` compiles, `make clippy` clean (and `make miri` for an
   `unsafe` kata); add a row to `rust-katas/README.md`.

---

## Python (`python-katas/`)

A **senior-Python** module framed as **LLD scenarios** (real systems, like `java-katas`), *not*
"implement a language feature from scratch" drills. Each kata is a small system with behavioural
rules; a distinctive Python idiom (the data model, context managers, generators, decorators, asyncio,
`match`, Protocols) is the elegant *tool*, surfaced in "The real challenge".

- **Python 3.11+, standard library ONLY**, fully **type-hinted** (`X | None`, `list[str]`). Lint with
  **ruff** (globally installed); the solution must be `ruff check` clean (line-length 100, `py311`;
  config in `pyproject.toml`).
- **Test framework: pytest in a `.venv`** (matches `postgres-katas`). Each solution kata carries a
  `test_<kata>.py` importing via `from . import ...`. **Async katas are tested with `asyncio.run(...)`
  inside plain `def test_...()` — NO `pytest-asyncio`.**
  ```bash
  cd python-katas
  make venv        # python3 -m venv .venv + pip install pytest  (once)
  make solution    # pytest solution -q — reference suite GREEN
  make practice    # compileall + import every skeleton — proves they parse/import
  make lint        # ruff check solution practice
  .venv/bin/pytest solution/<kata> -q     # one kata
  ```
- **Per-kata = a package dir** at the same path in both trees: `solution/<kata>/__init__.py` (impl +
  interview-grade module docstring) + `solution/<kata>/test_<kata>.py`; `practice/<kata>/__init__.py`
  (skeleton) + `practice/<kata>/README.md`. `solution/` and `practice/` each have an empty
  `__init__.py`.
- **Scenario framing is mandatory** (this is what makes a kata, not a drill): the kata is a **real
  system with behavioural rules** (a spreadsheet, a mini-Redis, a file system — see `java-katas`
  `orderbook`/`circuitbreaker` for tone), and the Python idiom is deferred to "The real challenge".
  Do NOT ship a kata whose title is "implement a decorator / a generator / a context manager". Mix
  domains (mastery-forward, light trading flavour — money angle only on trading katas).
- **Skeleton idiom:** `raise NotImplementedError`. Copy fixtures/domain types (dataclasses, enums,
  Protocols, custom exceptions) **verbatim**; keep public signatures identical. A skeleton only
  *defines* classes/functions at import time, so `make practice` (compileall + import) is the gate —
  `NotImplementedError` fires only when a body is called.
- **Solution module docstrings are interview-grade** — the scenario, the design/pattern choice
  (Strategy/State/Observer/…), the trade-off + named alternatives, and (trading katas only) the money
  angle — matching `spreadsheet`/`workflow`.

### Recipe: add a new Python kata

1. **Pick a real system/scenario** (an LLD-style component with rules), not a language-feature drill;
   choose a lower-case `<name>`. The README leads with a `>` scenario and defers the idiom to "The
   real challenge".
2. In **`solution/<name>/`**: `__init__.py` (fixtures + impl + module docstring) and
   `test_<name>.py` (pytest, `from . import ...`, `asyncio.run` for async). `pytest solution/<name>` →
   green; `ruff check solution/<name>` → clean.
3. Mirror into **`practice/<name>/`** (no tests): fixtures verbatim + the same signatures with
   `raise NotImplementedError`; add a `README.md` (Java scenario 8-section format).
4. Verify: `make solution` GREEN, `make practice` imports, `make lint` clean; add a row to
   `python-katas/README.md`. (No central registry — pytest auto-discovers.)

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
