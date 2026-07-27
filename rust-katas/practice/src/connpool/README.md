# Connection Pool

> A bounded pool of pre-opened connections that workers borrow and return — where returning is automatic, because the borrow guard's `Drop` puts the connection back.

## The problem

Opening a database (or venue) connection is expensive, so keep a fixed pool of pre-opened ones. A
worker borrows a connection, uses it, and returns it. The pool is bounded: when all connections are
out, a borrower either **blocks** until one comes back, or gives up immediately. Many threads borrow
and return at once, and no two may ever hold the same connection.

## Requirements

- `Pool::new(capacity, factory)` pre-creates `capacity` connections via `factory`; panics if
  `capacity == 0`.
- `acquire()` returns a guard borrowing one connection, **blocking** until one is free.
- `try_acquire()` returns `Some(guard)` if one is free right now, else `None` — never blocks.
- Dropping the guard **returns** the connection to the pool automatically (even on early return /
  panic). The guard derefs to the connection so you can use it directly.
- `available()` = free count; `capacity()` = pool size. Safe for many concurrent borrowers.

## What you implement

- `Pool<T>`: `new`, `acquire`, `try_acquire`, `available`, `capacity`, and `Clone`.
- `PooledConn<T>`: `Deref`/`DerefMut` to `T`, **and a `Drop` that returns the connection** (you add
  the `Drop` impl — it's the point).

## The real challenge

- **RAII: the guard's `Drop` *is* the return.** Don't expose a `release()` the caller must remember —
  make dropping the guard return the connection, so it's leak-free by construction (the `MutexGuard` /
  `File` pattern). Add the `impl Drop for PooledConn`.
- **`Option<T>` so `Drop` can move the value out.** You can't move a field out of `&mut self` in
  `Drop`; hold the connection as `Option<T>` and `take()` it in `Drop`.
- **`Deref`/`DerefMut`** make the guard usable as the connection (`conn.query()`).
- **Blocking borrow with `Condvar`.** Free connections in a `Mutex<VecDeque<T>>`; `acquire` waits on a
  `Condvar` when empty and a returning guard `notify`s it. Re-check the predicate in a `while` loop
  after `wait` (spurious wakeups).

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests`
(cover borrow/return via `available()`, `try_acquire` exhaustion, that `Drop` returns the connection,
a blocking-`acquire`-wakes-on-return thread test, and a `Barrier`-gated stress asserting no more than
`capacity` are ever out), then:

```
cd rust-katas && cargo test -p practice connpool
```

## Reference

Worked solution: `rust-katas/solution/src/connpool/`.

Extension: add `acquire_timeout(Duration) -> Option<guard>` (via `Condvar::wait_timeout`); or back the
pool with a counting semaphore; or validate/recycle a connection on return.

Background: [The Rust Book — the `Drop` trait](https://doc.rust-lang.org/book/ch15-03-drop.html) and
[`std::sync::Condvar`](https://doc.rust-lang.org/std/sync/struct.Condvar.html).
