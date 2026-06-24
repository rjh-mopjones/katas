# Caches — TTL, LRU, LFU

> Build the three classic in-memory caches from scratch: a concurrent TTL cache, an O(1) LRU, and
> (read-only) an O(1) LFU.

One kata, three exercises in one package (`package cache`). You implement the skeletons; **write your
own tests** under `practice/cache/` to drive them. The `solution/cache/` twin is the answer key —
full implementations, interview-grade doc comments, and a reference test suite to compare against
afterwards.

## Exercise 01 — concurrent TTL cache (`ttlcache.go`)

### The problem
A generic, thread-safe cache whose entries expire after a TTL, with a stampede-proof compute-on-miss.

### Requirements
- `Get`/`Set`, generic over `Cache[K comparable, V any]`; `NewCache(defaultTTL, opts...)`.
- **Injectable clock** (`WithClock`) so TTL is deterministic in tests — no `time.Sleep` flakiness.
- **Lazy expiry on `Get`** (return a miss if expired) **and** a background sweeper goroutine for
  eager eviction.
- `Close()` stops the sweeper with **no goroutine leak** (a `done` channel or context).
- `Delete`/`Invalidate` and `Clear` for explicit invalidation.
- `GetOrCompute(key, fn)`: on a miss, compute and store; N concurrent misses on the same key run
  `fn` **exactly once** (use `golang.org/x/sync/singleflight`).

### The real challenge
Lock discipline: `sync.RWMutex`, `RLock` on the read path, full `Lock` to mutate. The
read-modify-write store inside `GetOrCompute` **must** take the full `Lock` — `singleflight` only
dedups the *same* key, so distinct-key stores would otherwise race the map (Go's fatal *concurrent
map writes*). Plus the sweeper goroutine-leak risk on `Close`.

### What you implement
`Cache[K, V]`: `NewCache`, `WithClock`, `WithSweepInterval`, `Get`, `Set`, `Delete`, `Invalidate`,
`Clear`, `GetOrCompute`, `Len`, `Close`.

## Exercise 02 — O(1) LRU cache (`lru.go`)

### The problem
A fixed-capacity cache with O(1) `Get` and `Put` that evicts the least-recently-used entry.

### Requirements
- `LRUCache[K, V]` with `NewLRU(capacity)`, `Get`, `Put`, `Len`.
- Internals: `map[K]*node` + a **hand-rolled** doubly-linked list (no `container/list`). Sentinel
  head/tail to avoid nil-edge bugs.
- Helpers to implement: `addToFront`, `unlink`, `moveToFront`, `removeTail`.

### The real challenge
`Get` mutates recency — **a read is a write**. Verbalise that, the sentinel trick, and the O(1)
justification (map for lookup, list for ordering). Optional second pass: a mutex-wrapped concurrent
variant — and note that `Get` needs a full `Lock`, not `RLock`, because it reorders the list.

## Exercise 03 — O(1) LFU cache (read-only)

No skeleton — this is whiteboard prep. Read `solution/cache/lfu.go` for the frequency-bucket
structure (`map[freq]*list`, a `minFreq` pointer) and the LRU-vs-LFU trade-off you should be able to
deliver out loud.

## Run

There are no tests here — **write your own** under `practice/cache/`, then:

```bash
cd go-katas/practice && go test -race ./cache/
```

Compare against the reference suite in `solution/cache/` when you're done.

## Reference
- Worked solution: `solution/cache/` (`ttlcache.go`, `lru.go`, `lfu.go` + tests).
- `golang.org/x/sync/singleflight` — the stampede guard for Exercise 01.
- Java Interview Primer topics: thread safety, optimistic vs pessimistic locking, atomic
  check-and-act; plus the classic LRU (LeetCode 146) and LFU (LeetCode 460) structures.
