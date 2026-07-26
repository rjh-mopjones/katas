use std::hash::Hash;
use std::marker::PhantomData;

/// A fixed-capacity cache that evicts the least-recently-used entry. You choose the internals.
pub struct LruCache<K, V> {
    _marker: PhantomData<(K, V)>,
}

impl<K: Hash + Eq + Clone, V> LruCache<K, V> {
    /// Create a cache holding at most `capacity` entries. Panics if `capacity == 0`.
    pub fn new(_capacity: usize) -> Self {
        todo!("implement LruCache::new")
    }

    /// Look up `key`, marking it most-recently-used. Returns `None` if absent.
    pub fn get(&mut self, _key: &K) -> Option<&V> {
        todo!("implement get")
    }

    /// Read `key` without changing recency. Returns `None` if absent.
    pub fn peek(&self, _key: &K) -> Option<&V> {
        todo!("implement peek")
    }

    /// Insert or update `key`, marking it most-recently-used; evict the LRU entry when over capacity.
    pub fn put(&mut self, _key: K, _value: V) {
        todo!("implement put")
    }

    /// The number of entries currently held.
    pub fn len(&self) -> usize {
        todo!("implement len")
    }

    /// Whether the cache holds no entries.
    pub fn is_empty(&self) -> bool {
        todo!("implement is_empty")
    }

    /// The maximum number of entries the cache can hold.
    pub fn capacity(&self) -> usize {
        todo!("implement capacity")
    }
}
