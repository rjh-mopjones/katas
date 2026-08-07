# Connection Pool

## Approach
`ConnectionPool<R>` bounds concurrency with a `Semaphore` sized to `maxSize`: `borrow` calls
`tryAcquire(timeout, unit)`, which blocks until a slot frees up or the deadline passes, cleanly
separating "may I proceed at all?" from "which specific resource do I get?" — the semaphore
answers the first question, an unbounded lock-free `ConcurrentLinkedQueue<R>` of idle resources
answers the second. Resources are created lazily on first borrow rather than pre-allocated at
construction, and `AtomicInteger totalCreated` distinguishes "idle queue is empty because nothing's
been created yet" from "idle queue is empty because everything's in use" — once a permit is
acquired, an empty idle queue simply means create-a-new-one is safe up to `maxSize`.

Before handing out an idle resource, `borrow` runs it through the optional `Predicate<R> validator`
(default: always-valid). A resource that fails validation is discarded and replaced transparently
in the same call, keeping the pool self-healing without a background health-check thread. `release`
deliberately offers the resource back to the idle queue *before* releasing the semaphore permit —
reversing that order would let another thread acquire the freed permit and find the idle queue
still empty, causing it to manufacture an extra resource beyond `maxSize`.

`borrow` returns `null` on timeout rather than throwing, letting callers cheaply decide to retry,
fail, or fall back without paying for exception construction/stack-walking on a path that may be
common under load. The semaphore is intentionally non-fair — FIFO ordering of waiters is rarely
worth the throughput cost in pool scenarios, and since timed-out threads hold no permit, the
non-fair race is safe.

## The real challenge
- **Semaphore as the bound**: the semaphore models "slots available" cleanly — `tryAcquire(timeout, unit)` blocks until a slot opens or the deadline elapses. Without it, you would have to manually count in-flight borrows and coordinate that count with the idle queue.
- **Release order matters**: in `release`, add the resource to the idle queue _before_ releasing the semaphore permit. If you release the permit first, another thread can acquire it and find the idle queue empty, causing it to create an extra resource beyond `maxSize`.
- **Validate-on-borrow loop**: after acquiring a permit, you may pull an invalid idle resource; you must discard it and try again (or create fresh), all while holding exactly one permit.
- **Lazy creation vs idle-queue empty**: an empty idle queue after a successful `acquire` means create a new resource only if `totalCreated < maxSize` — the semaphore's available permits encode this invariant.

## Common mistakes & senior signal
- Releasing the semaphore permit before offering the resource back to the idle queue — the classic bug here; it lets a racing thread observe "permit available, queue empty" and over-create resources past `maxSize`.
- Forgetting `borrow` must loop past invalid resources rather than returning `null`/failing on the first stale one — the pool must self-heal, not surface transient staleness to the caller.
- Not distinguishing "queue empty, haven't hit `maxSize` yet" from "queue empty, everything's borrowed" — without `totalCreated` (or equivalent), you either over-create or spuriously block.
- Sizing the pool far larger than `(core_count × 2) + spindle_count` "to be safe" — a strong candidate can state the HikariCP sizing heuristic and explain why bigger pools are usually slower, not faster (memory pressure, context-switch overhead, DB-side connection cost).
- Treating `release` as safe to call twice or with a foreign resource — it isn't; double-release over-releases the semaphore and silently lets more than `maxSize` callers hold a resource concurrently.

## Extensions
- Add **maximum connection lifetime** so stale long-lived TCP sessions get recycled even if they keep passing validation.
- Add **idle eviction**: return resources to the underlying system during quiet periods instead of holding them indefinitely.
- Add **keep-alive pings** on idle resources to catch staleness before a caller's request fails on it.
- Expose **metrics** (borrow wait time, creation count, validation failure rate) for JMX/observability.
- Add **statement caching** (JDBC-specific) or, more generally, a per-resource cache the pool manages alongside the connection itself.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/connectionpool/`)
- Java Interview Primer: Q191 (HikariCP sizing), Q48 (semaphores)
