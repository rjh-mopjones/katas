//! LRU Cache — a fixed-capacity cache that evicts the **least-recently-used** entry, all ops O(1).
//!
//! # The component
//!
//! A bounded cache sits in front of a slow store (disk, network, a DB). It holds at most `capacity`
//! entries. A hit ([`get`](LruCache::get)) returns the value and marks that key *most-recently-used*;
//! a [`put`](LruCache::put) that would overflow first evicts the *least-recently-used* key. To hit
//! every operation in O(1) you need two structures working together: a hash map for O(1) lookup, and
//! a recency-ordered list you can splice a node out of and re-insert at the front in O(1).
//!
//! # Why this is *the* Rust data-structure lesson
//!
//! In C++ or Java the recency list is a doubly-linked list of heap nodes and you keep a raw pointer
//! from the map's value straight to each node; moving a node to the front is a couple of pointer
//! swaps. That design **does not port to safe Rust**. A doubly-linked list means every node is
//! pointed at from two directions — its `prev` and its `next` — i.e. it has *two owners*, and Rust's
//! ownership model allows exactly one. The naive fix, `Rc<RefCell<Node>>` with `Weak` back-links,
//! compiles but is genuinely painful: every field access becomes a runtime `borrow_mut()` that can
//! panic, the `Weak` upgrades clutter the logic, and you pay a refcount + interior-mutability tax on
//! the hot path.
//!
//! # The index/slab approach
//!
//! The idiomatic answer is to **not use pointers at all**. Store the nodes in a `Vec<Entry>` — an
//! arena / slab — and represent the "pointers" as `usize` indices into that vec. Each [`Entry`] holds
//! `prev: Option<usize>` and `next: Option<usize>`; `head`/`tail` are index fields; a free-list
//! (`Vec<usize>`) tracks reclaimed slots so eviction can recycle a slot without shrinking the vec.
//! Moving a node to the front is the same unlink-then-relink surgery a pointer list does — a handful
//! of field writes, O(1) — but every access is a bounds-checked slice index the borrow checker is
//! completely happy with. No `unsafe`, no `Rc`, no `RefCell`. Slot indices stay valid for the life of
//! the entry (eviction recycles a slot via the free-list rather than shifting the vec), so the map's
//! stored indices never dangle.
//!
//! # Why `get` takes `&mut self`
//!
//! A cache read is a *write*: touching a key reorders the recency list. So [`get`](LruCache::get)
//! borrows `&mut self` even though it hands back a `&V`. For a pure read that must not disturb
//! recency, that is exactly what [`peek`](LruCache::peek) is for — `&self`, no reordering.
//!
//! # The map + intrusive list pairing
//!
//! `HashMap<K, usize>` maps a key to its slab slot for O(1) lookup; the slab's `prev`/`next` links
//! give O(1) recency reordering and eviction. Neither alone is enough — a `HashMap` has no order, a
//! list has no lookup — so the two are kept in lockstep. The key lives in both the map and the
//! `Entry` (eviction needs the tail slot's key to remove the map row), which is why the impl bounds
//! `K: Hash + Eq + Clone`.
//!
//! # Alternatives
//!
//! In production you'd reach for the [`lru`](https://crates.io/crates/lru) crate or
//! `hashlink::LinkedHashMap`, both of which package exactly this arena-list design behind a safe API.
//! The `Rc<RefCell<Node>>` + `Weak` back-link approach is the other "from scratch" answer — correct,
//! but the interior-mutability tax and panic-on-borrow footguns make the index/slab version the one
//! to reach for.

use std::collections::HashMap;
use std::hash::Hash;

/// One slot in the recency list: the stored pair plus its neighbours' slab indices.
struct Entry<K, V> {
    key: K,
    value: V,
    prev: Option<usize>,
    next: Option<usize>,
}

/// A fixed-capacity cache that evicts the least-recently-used entry.
///
/// Lookup is via a `HashMap<K, usize>`; recency ordering is an intrusive doubly-linked list stored as
/// indices into a `Vec<Entry>` slab, so every operation is O(1) amortised with no `unsafe`.
pub struct LruCache<K, V> {
    map: HashMap<K, usize>,
    slab: Vec<Entry<K, V>>,
    free: Vec<usize>,
    head: Option<usize>,
    tail: Option<usize>,
    capacity: usize,
}

impl<K: Hash + Eq + Clone, V> LruCache<K, V> {
    /// Create a cache holding at most `capacity` entries.
    ///
    /// # Panics
    ///
    /// Panics if `capacity == 0` — a zero-capacity cache can hold nothing, so it is almost certainly
    /// a bug at the call site rather than something to handle at runtime.
    pub fn new(capacity: usize) -> Self {
        assert!(capacity > 0, "LruCache capacity must be greater than 0");
        LruCache {
            map: HashMap::with_capacity(capacity),
            slab: Vec::with_capacity(capacity),
            free: Vec::new(),
            head: None,
            tail: None,
            capacity,
        }
    }

    /// Look up `key`, marking it most-recently-used. Returns `None` if absent.
    ///
    /// Takes `&mut self` because a hit reorders the recency list — a read is a write. Use
    /// [`peek`](Self::peek) for a read that leaves recency untouched.
    pub fn get(&mut self, key: &K) -> Option<&V> {
        let idx = *self.map.get(key)?;
        self.move_to_front(idx);
        Some(&self.slab[idx].value)
    }

    /// Read `key` **without** changing recency. Returns `None` if absent.
    pub fn peek(&self, key: &K) -> Option<&V> {
        let idx = *self.map.get(key)?;
        Some(&self.slab[idx].value)
    }

    /// Insert or update `key`, marking it most-recently-used.
    ///
    /// Updating an existing key overwrites its value without growing the cache. Inserting a new key
    /// when the cache is full first evicts the least-recently-used entry.
    pub fn put(&mut self, key: K, value: V) {
        if let Some(&idx) = self.map.get(&key) {
            self.slab[idx].value = value;
            self.move_to_front(idx);
            return;
        }

        if self.map.len() == self.capacity {
            self.evict_lru();
        }

        let idx = self.alloc(Entry {
            key: key.clone(),
            value,
            prev: None,
            next: self.head,
        });
        if let Some(old_head) = self.head {
            self.slab[old_head].prev = Some(idx);
        }
        self.head = Some(idx);
        if self.tail.is_none() {
            self.tail = Some(idx);
        }
        self.map.insert(key, idx);
    }

    /// The number of entries currently held.
    pub fn len(&self) -> usize {
        self.map.len()
    }

    /// Whether the cache holds no entries.
    pub fn is_empty(&self) -> bool {
        self.map.is_empty()
    }

    /// The maximum number of entries the cache can hold.
    pub fn capacity(&self) -> usize {
        self.capacity
    }

    /// Unlink `idx` from wherever it sits and splice it in at the head (most-recently-used).
    fn move_to_front(&mut self, idx: usize) {
        if self.head == Some(idx) {
            return;
        }
        self.unlink(idx);
        self.slab[idx].prev = None;
        self.slab[idx].next = self.head;
        if let Some(old_head) = self.head {
            self.slab[old_head].prev = Some(idx);
        }
        self.head = Some(idx);
    }

    /// Splice `idx` out of the recency list, fixing up its neighbours and the head/tail fields.
    fn unlink(&mut self, idx: usize) {
        let prev = self.slab[idx].prev;
        let next = self.slab[idx].next;
        match prev {
            Some(p) => self.slab[p].next = next,
            None => self.head = next,
        }
        match next {
            Some(n) => self.slab[n].prev = prev,
            None => self.tail = prev,
        }
    }

    /// Drop the least-recently-used entry (the tail) and reclaim its slot for reuse.
    fn evict_lru(&mut self) {
        let Some(idx) = self.tail else { return };
        self.unlink(idx);
        // Remove the map row now; the dead value stays in the slot until `alloc` overwrites it.
        let key = self.slab[idx].key.clone();
        self.map.remove(&key);
        self.free.push(idx);
    }

    /// Place `entry` into a recycled slot if one is free, else push a new slot; return its index.
    fn alloc(&mut self, entry: Entry<K, V>) -> usize {
        if let Some(idx) = self.free.pop() {
            self.slab[idx] = entry; // drops the dead entry that occupied this reclaimed slot
            idx
        } else {
            self.slab.push(entry);
            self.slab.len() - 1
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn put_then_get_round_trips() {
        let mut cache: LruCache<i32, &str> = LruCache::new(2);
        cache.put(1, "one");
        cache.put(2, "two");
        assert_eq!(cache.get(&1), Some(&"one"));
        assert_eq!(cache.get(&2), Some(&"two"));
        assert_eq!(cache.get(&3), None);
    }

    #[test]
    fn evicts_least_recently_used_when_over_capacity() {
        let mut cache: LruCache<i32, &str> = LruCache::new(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three"); // capacity 2 -> key 1 (oldest) evicted
        assert_eq!(cache.get(&1), None);
        assert_eq!(cache.get(&2), Some(&"two"));
        assert_eq!(cache.get(&3), Some(&"three"));
        assert_eq!(cache.len(), 2);
    }

    #[test]
    fn get_updates_recency_so_a_different_key_is_evicted() {
        let mut cache: LruCache<i32, &str> = LruCache::new(2);
        cache.put(1, "one");
        cache.put(2, "two");
        // Touch key 1 so it is now most-recently-used; key 2 becomes the LRU.
        assert_eq!(cache.get(&1), Some(&"one"));
        cache.put(3, "three"); // evicts key 2, NOT key 1
        assert_eq!(cache.get(&1), Some(&"one"));
        assert_eq!(cache.get(&2), None);
        assert_eq!(cache.get(&3), Some(&"three"));
    }

    #[test]
    fn updating_existing_key_does_not_grow_len_and_marks_mru() {
        let mut cache: LruCache<i32, &str> = LruCache::new(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(1, "ONE"); // update in place: len stays 2, key 1 now MRU (key 2 is LRU)
        assert_eq!(cache.len(), 2);
        assert_eq!(cache.peek(&1), Some(&"ONE"));
        cache.put(3, "three"); // evicts key 2, not the just-updated key 1
        assert_eq!(cache.get(&2), None);
        assert_eq!(cache.get(&1), Some(&"ONE"));
    }

    #[test]
    fn peek_does_not_change_recency() {
        let mut cache: LruCache<i32, &str> = LruCache::new(2);
        cache.put(1, "one");
        cache.put(2, "two");
        // Peek the oldest key — recency must be unaffected.
        assert_eq!(cache.peek(&1), Some(&"one"));
        cache.put(3, "three"); // key 1 is still the LRU, so it is evicted
        assert_eq!(cache.get(&1), None);
        assert_eq!(cache.get(&2), Some(&"two"));
        assert_eq!(cache.get(&3), Some(&"three"));
    }

    #[test]
    fn len_is_empty_and_capacity_report_correctly() {
        let mut cache: LruCache<String, i32> = LruCache::new(3);
        assert!(cache.is_empty());
        assert_eq!(cache.len(), 0);
        assert_eq!(cache.capacity(), 3);
        cache.put("a".to_string(), 1);
        assert!(!cache.is_empty());
        assert_eq!(cache.len(), 1);
        assert_eq!(cache.capacity(), 3);
    }

    #[test]
    fn eviction_keeps_len_at_capacity() {
        let mut cache: LruCache<i32, i32> = LruCache::new(2);
        for i in 0..10 {
            cache.put(i, i * 10);
            assert!(cache.len() <= 2);
        }
        assert_eq!(cache.len(), 2);
        // Only the two most-recent survive; the slot free-list is exercised on every put past 2.
        assert_eq!(cache.get(&8), Some(&80));
        assert_eq!(cache.get(&9), Some(&90));
        assert_eq!(cache.get(&7), None);
    }

    #[test]
    #[should_panic]
    fn zero_capacity_panics() {
        let _: LruCache<i32, i32> = LruCache::new(0);
    }
}
