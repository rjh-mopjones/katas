# Hash Map

> Build a hash table from scratch — the data structure behind `java.util.HashMap`.

## The problem
Implement a generic key-value map backed by your own hash table, not `java.util.HashMap`. You own the
bucket array, the collision strategy, and the resize/rehash mechanics — the mechanics every
"how does `HashMap` work" interview question eventually gets to.

## Requirements
- `MyHashMap()` and `MyHashMap(int initialCapacity)`.
- `V put(K key, V value)` — inserts or overwrites, returns the previous value or `null`.
- `V get(K key)` — returns the value, or `null` if absent.
- `V remove(K key)` — removes and returns the value, or `null` if absent.
- `int size()`.
- `boolean containsKey(K key)`.
- A `null` key must be supported (it lives in one well-defined bucket, not rejected).
- Growth: once a load factor of `0.75` is exceeded, the table must resize (double) and rehash every
  entry — capacity never grows unbounded per `put`, and no entry is ever lost across a resize.

## What you implement
Implement `MyHashMap<K, V>` from scratch: the two constructors, `put`, `get`, `remove`, `size`,
`containsKey`. You design the bucket array, the collision strategy, the hash-to-index mapping, and the
resize/rehash mechanics yourself.

## The real challenge
- **Separate chaining**: each bucket holds a singly-linked chain of entries; two keys landing in the
  same bucket must both survive, distinguished by `equals`, never by hash alone.
- **Spreading the hash**: `key.hashCode()` alone clusters badly when only its low bits vary. XOR-folding
  the high bits into the low bits (`h ^ (h >>> 16)`) spreads that entropy before masking it down to an
  index.
- **Power-of-two capacity**: keeping capacity a power of two turns `hash % capacity` into
  `hash & (capacity - 1)` — one AND instead of a division — but it is only correct if every resize also
  lands on a power of two.
- **Load factor and resize**: past 0.75 load, chains get long enough that lookups stop being O(1).
  Resizing means allocating a bigger table and re-deriving every entry's bucket index against the new
  capacity — get this wrong and entries silently vanish after a resize.
- **The null key**: `null.hashCode()` throws, so `null` needs a special-cased hash (conventionally
  bucket `0`) rather than falling through the normal `hashCode()` path.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/hashmap/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/hashmap/`
- Java Interview Primer: hashing / the `equals`-`hashCode` contract
