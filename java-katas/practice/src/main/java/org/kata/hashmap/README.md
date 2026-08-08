# Hash Map

> Build a hash table from scratch — the data structure behind `java.util.HashMap`.

## The problem
Implement a generic key-value map backed by your own hash table, not `java.util.HashMap`. You own the
bucket array, the collision strategy, and the resize/rehash mechanics — the mechanics every
"how does `HashMap` work" interview question eventually gets to.

## Requirements
- Store key→value pairs; inserting a key that already exists overwrites it and yields the previous value.
- Look a value up by its key; an absent key reports "not present".
- Remove a key, yielding whatever value it held.
- Report how many entries are held, and whether a given key is present.
- A `null` key must be supported (it lives in one well-defined bucket, not rejected).
- Once a load factor of `0.75` is exceeded, the table resizes (doubles) and rehashes every entry —
  capacity never grows unbounded per insert, and no entry is ever lost across a resize.

## What you're given
Nothing but the problem. You design the entire public API — method names, parameters, return types —
and the internals (bucket array, collision strategy, hash-to-index mapping, resize/rehash) from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/hashmap/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
