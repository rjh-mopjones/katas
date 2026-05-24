# Practice

Implement the katas here. Every file already has its **package, imports, Javadoc, field
declarations, constructors, and method signatures** — only the logic-method bodies are blanked to:

```java
throw new UnsupportedOperationException("TODO: implement");
```

The tests under `practice/src/test` are identical to the solution's. They are **RED** until you
implement the method. Make them green.

## Workflow

```bash
# JDK 21 is required (the default `mvn` JDK may be older — see ../README.md)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Work on practice (run from repo root)
mvn -pl practice test                          # all practice tests (RED until you implement)
mvn -pl practice test -Dtest=LruCacheTest      # one kata at a time

# The reference, always green
mvn -pl solution test
```

## Side-by-side with the solution

Every practice file has a twin at the **same relative path** under `../solution/`:

```
practice/src/main/java/org/kata/cache/LruCache.java   <- you implement
solution/src/main/java/org/kata/cache/LruCache.java   <- the worked solution
```

Open both in a split editor (IntelliJ: right-click a tab → *Split Right*) to glance at the
solution when stuck. Or diff them:

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
