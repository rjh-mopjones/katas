# Rate Limiters

## Approach
All three limiters share one structural skeleton and differ only in the per-key state they carry
and the admission arithmetic. Understanding the common skeleton first makes each algorithm a small
delta.

**Shared skeleton (all three).** State lives in a `ConcurrentHashMap<String, AtomicReference<State>>`.
`computeIfAbsent` does atomic lazy per-key initialisation (the factory runs at most once even under
concurrent first-touch). Every acquire is a **lock-free CAS retry loop**: snapshot the immutable
state record, compute the proposed next state from the elapsed time since the last update, and
`compareAndSet`; on failure another thread won the race, so re-read and retry. Time is derived
**lazily** on each call (no background ticker thread), so an idle key costs zero CPU. The clock is
an injectable `LongSupplier` — `System::nanoTime` in production, a fake in tests — and elapsed time
is clamped `Math.max(0, …)` to defend against cross-thread `nanoTime` anomalies.

**Token bucket** — state is `record BucketState(long tokens, long lastRefillNanos)`. Buckets start
**full**. On each call, refill = `min(capacity, tokens + elapsed × refillPerNano)`; if `refilled < n`
reject, else CAS to `(refilled - n, now)`. `refillPerSec` is precomputed to tokens-per-nanosecond
once so the hot path multiplies rather than divides. Allows controlled bursts up to `capacity`.

**Leaky bucket** — state is `record BucketState(double level, long lastLeakNanos)`. Buckets start
**empty**. The "water level" drains continuously: `newLevel = max(0, level - elapsed × leakPerNano)`;
admit only if `newLevel + n <= capacity`, then CAS to `(newLevel + n, now)`. `level` is a `double`
so sub-nanosecond drain volumes accumulate instead of rounding to zero. Enforces a hard throughput
ceiling — no burst headroom, perfectly smooth output.

**Sliding window counter** — state is `record WindowState(long prevCount, long currCount, long windowStart)`.
It approximates a true sliding window with two consecutive fixed windows, weighting the previous
window by how much of it still overlaps the trailing window:
`estimatedCount = prevCount × (1 − elapsedInCurrent/windowSize) + currCount`. On each call it ages
the counters forward by `windowsElapsed = elapsed / windowNanos` (0 → no change; 1 → current becomes
previous, new current starts at the fixed boundary so windows don't drift; ≥2 → both reset). Admit
if `estimatedCount + n <= limit`, then CAS incrementing `currCount`. O(1) memory per key with a
bounded (~±1 request) approximation error. This is the technique Cloudflare and Nginx use.

## The real challenge
- **Compound-state CAS**: both fields of each state record (`tokens`+`lastRefillNanos`, `level`+`lastLeakNanos`, `prevCount`+`currCount`+`windowStart`) must be swapped atomically as one immutable record in an `AtomicReference`. Using two separate `AtomicLong`s would allow a reader to observe a half-updated pair, silently double-refilling or double-draining.
- **Lazy time-based update**: no background thread — derive how much should have accumulated/leaked since `lastNanos` on every call. An idle key costs zero CPU between calls.
- **Fast-reject under load**: early-exit without attempting a CAS when the request is already known to fail. Under heavy rejection this prevents the CAS itself from becoming a contention bottleneck.
- **Algorithm contrast**: token bucket allows bursts up to capacity; leaky bucket enforces a hard throughput ceiling with no burst headroom; sliding window counter prevents boundary spikes at O(1) memory with a small approximation error.

## Common mistakes & senior signal
- **Two separate atomics for a compound state.** Splitting `tokens` and `lastRefillNanos` into two
  `AtomicLong`s is the classic trap: a reader can observe a fresh count paired with a stale timestamp
  and double-refill. The senior move is bundling the tuple into an immutable `record` so the CAS can
  only ever swap the whole thing.
- **CAS-ing on a rejected request.** Attempting the `compareAndSet` before checking whether the
  request can succeed turns a flood of rejections into write contention on the atomic — rejection
  itself becomes the bottleneck. Strong answers make failures pure reads and only writers succeed.
- **Using `currentTimeMillis()` for elapsed-time math.** Wall-clock time jumps backwards (NTP slew,
  manual changes, leap seconds), producing negative elapsed time that corrupts the refill/drain
  estimate. Monotonic `nanoTime` (injected as a `LongSupplier`) is mandatory, and the elapsed value
  should still be clamped `>= 0` defensively.
- **A background drainer/refiller thread.** Ticking tokens in on a scheduler adds a thread per limiter
  (or cross-key coordination) and burns CPU on idle keys. Lazy on-read computation is simpler, cheaper,
  and race-free.
- **Reaching for `synchronized`.** A per-bucket mutex serialises all readers, parks threads on
  contention (kernel transition, priority-inversion risk), and caps throughput on one lock. CAS keeps
  every transition in user space where only the loser pays — and only by re-doing a few nanoseconds.
- **Letting the fixed-window boundary drift.** In the sliding-window ager, starting the new current
  window at `now` rather than at `windowStart + windowNanos` makes windows shrink/grow over time and
  breaks the estimate. Snap to the fixed boundary.

## Extensions
- **Distributed rate limiting.** Move the per-key state to Redis. A fixed-window counter is just
  `INCR` + `EXPIRE`; the compound-state token/leaky bucket becomes a Redis **Lua script** that does
  the atomic read-refill/drain-decrement server-side (the CAS analogue). Shard by hashed key when one
  Redis node is no longer enough.
- **Exact sliding window.** Swap the counter approximation for a **sliding window log** — a Redis
  sorted set of request timestamps with TTL-based eviction — when you need exact accuracy at O(n)
  memory instead of the ±1 approximation.
- **Add a fixed-window limiter** to the same interface to contrast the cheapest (O(1), exact within a
  window) option against its boundary-spike weakness.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/ratelimit/`)
- Java Interview Primer: Q239 (rate-limiting algorithms), Q43 (atomic classes), Q241 (CAS)
