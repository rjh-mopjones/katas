# LRU / LFU Cache

## Approach
`LruCache` gets O(1) `get`/`put` from two cooperating structures: a `HashMap<K, Node>` for direct
lookup, and an intrusive doubly-linked list (each node carries its own `prev`/`next`) ordered by
recency — head is most-recently-used, tail is the eviction candidate. The map jumps straight to a
node so "move to front" is O(1) unlink + O(1) re-insert; a singly-linked list couldn't unlink an
arbitrary interior node in O(1). Sentinel head/tail nodes remove all the null-check edge cases at
the list boundaries. In production, reach for `LinkedHashMap` with `accessOrder=true` and an
overridden `removeEldestEntry` instead — this hand-rolled version exists because "how does LRU
actually work?" is a standard interview question, and the sentinel/intrusive-list trick is what
demonstrates real understanding.

`LfuCache` is the harder cousin: it needs the globally-minimum frequency in O(1), not just a
relative order. The trick (from the classic "O(1) LFU" algorithm) is a `Map<Integer,
LinkedHashSet<K>>` from frequency → ordered key set, plus a `minFreq` scalar. Eviction is always
`freqToKeys.get(minFreq)`'s first element. `LinkedHashSet` gives LRU tie-breaking within a
frequency bucket for free via insertion order. The subtle part is keeping `minFreq` correct: it
resets to 1 unconditionally on every new-key insert (a new key is by definition the least
frequent); on a promotion it only increments when the vacated bucket was both empty and the
current minimum.

`ConcurrentLruCache` wraps `LruCache` behind a single `ReentrantLock`. This is deliberate, not
lazy: every `get` mutates the recency list (move-to-front), so reads are not read-only — a
`ReadWriteLock` buys nothing because all callers need the write lock. Lock-striping by key doesn't
help either, since the shared tail pointer (the eviction candidate) can live in any stripe,
forcing cross-stripe coordination. A single lock is provably correct and easy to reason about; the
trade-off is that all threads serialize through one bottleneck.

## The real challenge
- **LRU in O(1)**: requires a `HashMap<K, Node>` combined with an intrusive doubly-linked list. The map provides O(1) lookup directly to the list node; the doubly-linked list provides O(1) unlink from any interior position. A singly-linked list cannot unlink an arbitrary node in O(1). Sentinel head/tail nodes eliminate null-pointer edge cases at the boundaries.
- **LFU in O(1)**: naive "scan for minimum frequency" is O(n). The O(1) trick: maintain a `Map<Integer, LinkedHashSet<K>>` from frequency to ordered key set, plus a `minFreq` scalar. Eviction is always `freqToKeys.get(minFreq).first()`. The hardest part is keeping `minFreq` correct: it resets to 1 on every new insertion; on a promotion it increments by 1 only if the old bucket is now empty and was the minimum.
- **Why `get` is not read-only in LRU**: every cache hit mutates the recency list (move-to-front). This means a `ReadWriteLock` cannot help — all callers need the write lock, making fine-grained locking impractical. A single `ReentrantLock` is the correct, simple approach.
- **`LinkedHashSet` for LFU tie-breaking**: insertion order within a frequency bucket gives LRU tie-breaking for free. A plain `HashSet` would make tie-breaking arbitrary; a `TreeSet` would add O(log n) cost.

## Common mistakes & senior signal
- Using a singly-linked list for LRU and discovering you can't unlink an interior node in O(1) — a strong answer explains up front *why* the list must be doubly-linked and intrusive.
- Forgetting sentinel nodes and special-casing empty-list / single-element inserts — the sentinel trick is worth stating explicitly as "eliminates four boundary cases down to one."
- In LFU, recomputing `minFreq` by scanning after every operation instead of maintaining it incrementally — this silently reintroduces O(n) and defeats the whole point of the exercise.
- Reaching for a `ReadWriteLock` on `ConcurrentLruCache` without first noticing that `get` mutates state — a candidate who tries this and self-corrects shows good instincts; one who ships it hasn't understood the access pattern.
- Not naming `LinkedHashMap`'s `accessOrder` + `removeEldestEntry` as the production shortcut — an interviewer wants to know you'd reach for the built-in first and only hand-roll when asked to.

## Extensions
- Swap `ConcurrentLruCache`'s single lock for a **striped approximate LRU**: partition the key space across N segments, each with its own lock and list; evict from whichever segment is fullest. Throughput scales with stripe count at the cost of a strictly-global recency order.
- Implement **sampled eviction** (Redis's approach): skip the recency list entirely, sample k random keys on eviction, and evict the least-recently-used among the sample — O(k) eviction, zero per-get overhead.
- Look at **Caffeine**'s design as the production end-state: near-lock-free reads via per-thread ring buffers of access events, drained asynchronously to maintain ordering (`W-TinyLFU`), rather than locking on every access.
- Add TTL-based expiry or a hit/miss-rate counter to either cache.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/cache/`)
- Java Interview Primer: Q96 (caching), Q154 (WeakHashMap/eviction), Q303
