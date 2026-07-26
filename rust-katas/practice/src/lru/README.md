# LRU Cache

> A bounded cache in front of a slow store: hold at most `capacity` entries and, when full, throw away the one nobody has touched in the longest — all operations O(1).

## The problem

Build a fixed-capacity cache keyed by `K` with values `V`. A `get` hit returns the value and marks
that key **most-recently-used**. A `put` inserts or updates; when inserting a new key would exceed
`capacity`, the **least-recently-used** entry is evicted first. `peek` reads a value *without*
touching recency. Every operation must be O(1) amortised — no scanning the cache to find the oldest
key.

## Requirements

- `new(capacity)` builds an empty cache; **panic if `capacity == 0`**.
- `get(&mut self, key)` returns `Some(&V)` on a hit and marks the key most-recently-used; `None` on a
  miss.
- `put(key, value)` inserts a new key or updates an existing one (in place — updating must **not**
  grow `len`), marking it most-recently-used. Inserting a new key when full evicts the LRU entry.
- `peek(&self, key)` returns `Some(&V)` **without** changing recency.
- `len` / `is_empty` / `capacity` report the current state.

## What you implement

```rust
pub struct LruCache<K, V> { /* your internals */ }

impl<K: std::hash::Hash + Eq + Clone, V> LruCache<K, V> {
    pub fn new(capacity: usize) -> Self;          // panic if capacity == 0
    pub fn get(&mut self, key: &K) -> Option<&V>; // marks key most-recently-used
    pub fn put(&mut self, key: K, value: V);      // insert/update; evicts LRU when over capacity
    pub fn peek(&self, key: &K) -> Option<&V>;    // read WITHOUT changing recency
    pub fn len(&self) -> usize;
    pub fn is_empty(&self) -> bool;
    pub fn capacity(&self) -> usize;
}
```

The struct body is yours to design. The provided skeleton uses a `PhantomData` marker just so it
compiles — replace it with real fields.

## The real challenge

- **The doubly-linked-list ownership problem.** O(1) LRU wants a hash map for lookup *and* a
  recency-ordered doubly-linked list you can splice a node out of and re-insert at the front. In
  C++/Java you keep a raw pointer from the map value to a heap node and swap a couple of pointers.
  That design **does not port to safe Rust**: a node in a doubly-linked list is pointed at from two
  directions (`prev` and `next`), so it has two owners — and Rust allows exactly one. The naive
  `Rc<RefCell<Node>>` + `Weak` back-link version compiles but is painful (runtime `borrow_mut()`
  panics, refcount + interior-mutability tax on the hot path).
- **Use indices, not pointers.** Store nodes in a `Vec<Entry>` slab and make the "pointers" `usize`
  indices: `Entry { key, value, prev: Option<usize>, next: Option<usize> }`, plus a
  `HashMap<K, usize>` (key → slab index), `head`/`tail` index fields, and a free-list `Vec<usize>`
  of reclaimed slots. Moving a node to most-recently-used is unlink-then-relink index surgery — a
  few field writes, O(1) — and every access is a bounds-checked index the borrow checker accepts.
  No `unsafe`.
- **`get` is `&mut self`.** A cache read *reorders* the recency list, so reading is writing —
  `get` takes `&mut self` even though it returns `&V`. `peek` is the `&self` read that leaves
  recency alone.
- **Generics + bounds.** `K: Hash + Eq` for the map; `Clone` because eviction needs the tail slot's
  key to remove its map row. Keep the map and list in lockstep — a mismatch is a leak or a dangling
  index.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` in
this file (cover eviction order, that `get` updates recency while `peek` does not, and in-place
update not growing `len`), then:

```
cd rust-katas && cargo test -p practice lru
```

## Reference

Worked solution: `rust-katas/solution/src/lru/`.

Extension: make it thread-safe by wrapping the cache in a `Mutex` (note that `get` needs `&mut`, so a
`RwLock` read guard is not enough); or add per-entry TTL so a stale entry is treated as a miss and
evicted on access.

Background: [The Rust Book — Rc<T>, the Reference Counted Smart Pointer](https://doc.rust-lang.org/book/ch15-04-rc.html)
(why the `Rc<RefCell<Node>>` linked-list design is the painful path this kata avoids).
