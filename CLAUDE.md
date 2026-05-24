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
