# Practice

This module ships two things and nothing more:

1. **Blank SUT skeletons** — every file has its package, imports, Javadoc, field declarations,
   constructors, and method signatures; only the logic bodies are blanked to:
   ```java
   throw new UnsupportedOperationException("TODO: implement");
   ```
2. **Per-kata READMEs** — problem statement, requirements, and hints, one per package under
   `src/main/java/org/kata/<pkg>/README.md`.

There are **no tests provided here**. Writing the tests is part of the exercise.

## Workflow

```bash
# JDK 21 is required (the default `mvn` JDK may be older — see ../README.md)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

For each kata:

1. **Read** the kata README (`src/main/java/org/kata/<pkg>/README.md`) for the problem statement
   and requirements.
2. **Implement** the class(es) from scratch — fill in the `TODO` method bodies.
3. **Write your own tests** under `src/test/java/org/kata/<pkg>/` to drive and verify your
   implementation.
4. **Run** your tests:
   ```bash
   mvn -pl practice test
   ```

When you want to compare or get unstuck, the `solution/` twin holds the full implementation
**and** the reference tests — peek at either after you have made your own attempt.

## Side-by-side with the solution

Every practice file has a twin at the **same relative path** under `../solution/`:

```
practice/src/main/java/org/kata/cache/LruCache.java   <- you implement
solution/src/main/java/org/kata/cache/LruCache.java   <- the worked solution
solution/src/test/java/org/kata/cache/               <- the reference tests
```

Open the implementation twins in a split editor (IntelliJ: right-click a tab → *Split Right*) to
glance at the solution when stuck. Or diff them:

```bash
diff practice/src/main/java/org/kata/cache/LruCache.java \
     solution/src/main/java/org/kata/cache/LruCache.java
```

## Suggested order (easier → harder)

1. `cache` — LRU then LFU (classic, high-frequency interview LLD)
2. `ratelimit` — token bucket → leaky bucket → sliding window
3. `circuitbreaker`, `retry`, `connectionpool` — resilience patterns
4. `blockingqueue` — lock + two conditions
5. `eventbus`, `idempotency`, `scheduler`, `aggregator` — async / messaging
6. `lockfree` — Treiber stack → Michael-Scott queue → ABA-safe stack (hardest)

Plus the original LLD katas: `bank`, `cinema`, `elevator`, `orderbook`, `parking`,
`restaurant`, `vending`.

> **Reset an attempt back to the stub:** there's no one-command reset yet (the repo isn't under
> git). Ask Claude to re-stub a file, or initialise git so `git checkout -- <file>` restores the
> stub. The solution copy is never touched, so you can always rebuild the stub from it.
