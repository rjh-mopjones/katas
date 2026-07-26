# Key-Value Store

> An in-memory mini-Redis — `set`/`get`/`delete` over string keys, with per-key TTL expiry and a
> transaction that commits as a whole or not at all.

## The problem

Build the engine behind a process-local key-value store: the kind of thing that backs a cache
sidecar or a feature-flag service. The API is tiny — `set`, `get`, `delete` — but two features make
it more than a `dict`. A key can carry a **TTL**, after which it disappears. And a block of writes
can be grouped into a **transaction** that either commits atomically or leaves the store untouched if
anything goes wrong.

## Requirements

- `set(key, value)` stores `value`; `get(key)` returns it, or `None` if the key is missing.
- `set(key, value, ttl=seconds)` makes the key expire `ttl` seconds after it was set, measured by an
  injected `clock`. `get` on an expired key returns `None`.
- Expiry is **lazy**: a key is only pruned when it's accessed. `__contains__` and `__len__` also
  treat expired keys as absent (`len` counts live keys only).
- A key with no TTL never expires.
- `delete(key)` removes a key and returns `True` if it was present and live, else `False`.
- `transaction()` is a **context manager**. Writes (`set`/`delete`) inside the `with` block are
  **buffered**, then applied **atomically on clean exit**. If the block raises, all buffered writes
  are **discarded** (rollback) and the exception propagates.
- Reads inside the block see the **committed** (pre-transaction) state, not the buffered writes
  (Redis `MULTI`-style: queued, not yet visible).

## What you implement

- `KVStore.set / get / delete`, `__contains__`, `__len__`, and `transaction()`.

No fixtures are provided — the whole class is yours to design. The constructor takes an injectable
`clock: Callable[[], float]` (default `time.monotonic`) so time is deterministic in tests.

## The real challenge

- **A context manager is the idiom for a unit of work.** `transaction()` returns something usable in
  a `with` block — write it as a `@contextlib.contextmanager` generator (setup before `yield`,
  teardown after) or as a class with `__enter__`/`__exit__`. Know both; pick one.
- **Buffer, then commit-or-rollback.** Don't touch the live store while a transaction is open —
  record pending writes in a side buffer. On clean exit, replay the buffer atomically. On error,
  drop it.
- **`__exit__` must let exceptions propagate.** Returning `False` (or, in the generator form, *not*
  swallowing the exception) is what re-raises. Returning `True` would silently eat the error — a
  classic bug.
- **Lazy expiry beats a background sweeper here.** Check a key's deadline when it's accessed and prune
  it on the way past — no thread scanning for dead keys. (Active expiry — a timer or random sampling,
  as real Redis also does — reclaims memory sooner but costs machinery. Know the trade-off.)
- **Inject the clock.** Elapsed-time logic reads from the passed-in `clock`, never `time.sleep` or
  wall-clock, so tests advance a fake clock instead of sleeping.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_kvstore.py` in this
directory (use a small fake clock with an `advance` method; cover TTL expiry, transaction commit,
and rollback-on-exception), then:

```
cd python-katas && .venv/bin/pytest practice/kvstore
```
Compare against the reference: `.venv/bin/pytest solution/kvstore`.

## Reference

Worked solution: `solution/kvstore/`.

Extension: add **pub/sub** (subscribe to key changes, publish on `set`/`delete`); or **nested
transactions** with savepoints (an inner `transaction()` that can roll back to its own start without
aborting the outer one) — the class-based `__enter__`/`__exit__` form carries the per-level state
more naturally.

Background: [Python `contextlib` — context manager utilities](https://docs.python.org/3/library/contextlib.html).
