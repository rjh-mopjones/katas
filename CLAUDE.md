# Katas — repo conventions

Multi-language kata repo for interview prep. This file is the authoring guide; follow it when
adding or changing katas so every language and kata stays consistent.

## The model (applies to every language)

Each `<lang>-katas/` has two mirrored trees:

- **`solution/`** — full reference implementation + tests. Must stay GREEN.
- **`practice/`** — the *same* tests, but the system-under-test (SUT) classes are blank skeletons
  the learner fills in from scratch. RED until implemented.

Rules that hold across languages:

- **Tests are the executable spec; the per-kata README is the human prompt.** Both live so the
  learner has a contract and a goal.
- **Practice SUT classes contain only the public API surface** (signatures that throw / are
  unimplemented) — no fields, no helper bodies, no explanatory comments. The learner designs the
  internals.
- **Fixture / domain types are real in practice** (interfaces, records, enums, value types, custom
  exceptions are copied verbatim from `solution/`) so the test module still compiles and can build
  inputs. The only RED comes from unimplemented SUT classes.
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
  mvn -pl solution test     # reference — green
  mvn -pl practice test     # your work — RED until implemented
  mvn -pl practice test -Dtest=LruCacheTest    # one kata
  ```
  `mvn test` from the root runs both — **practice fails by design**.
- **Package root:** `org.kata.<kata>`.
- **Tests:** JUnit Jupiter 5.11.3 ONLY — no Mockito, no AssertJ. Use `org.junit.jupiter.api.Test`
  + `Assertions.*`. Descriptive `snake_case` test names. Concurrency tests use a `CountDownLatch`
  gate + done with `Executors.newVirtualThreadPerTaskExecutor()`.
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
4. Mirror into **`practice/`**:
   - copy the test files verbatim;
   - copy the fixture/domain types (interfaces, records, enums, exceptions) verbatim;
   - for each SUT class, write a **bare skeleton**: `package` + existing `import`s + the class
     declaration + every non-`private` constructor/method signature with body
     `throw new UnsupportedOperationException();`. Delete Javadoc, fields, private methods, and
     private nested types (keep a public nested type only if a kept signature or a test needs it).
5. Add `practice/src/main/java/org/kata/<name>/README.md`: problem, requirements, *what you
   implement* (the public contract only), the real challenge, the run command, primer pointers.
6. Verify: `mvn -pl practice test-compile` succeeds; `mvn -pl practice test` is RED.

---

## Commits

- **Never add `Co-Authored-By` / Claude authorship** to commits.
- Conventional, concise messages. Keep tidying separate from behaviour changes.
- Remote: `https://github.com/rjh-mopjones/katas` (git root is this directory).

## Known gotchas

- `mvn test` from the root fails by design (practice is RED).
- `restaurant`'s `InMemoryBookingServiceRaceTest` is intentionally flaky — it asserts a data race
  *manifests*, which a warm JVM sometimes hides. Re-run if it fails in isolation; not a regression.
- A reference interview-topic source ("Java Interview Primer") drives which katas exist; new katas
  should map to a real interview topic and carry that pointer in their README.
