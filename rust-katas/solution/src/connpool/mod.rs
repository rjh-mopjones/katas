//! Connection Pool — a bounded pool of reusable resources with an RAII borrow guard.
//!
//! # The scenario
//!
//! Opening a database (or venue) connection is expensive, so a service keeps a fixed pool of
//! pre-opened connections. A worker *borrows* one, runs its query, and *returns* it. The pool is
//! bounded: when every connection is out, a borrower either blocks until one comes back
//! ([`Pool::acquire`]) or gives up immediately ([`Pool::try_acquire`]). Many threads borrow and return
//! concurrently, and no two may hold the same connection.
//!
//! # The Rust idiom: RAII — the guard's `Drop` *is* the return
//!
//! In most languages the pool hands you a connection and trusts you to call `release()` — forget it
//! on any error path and the pool leaks a slot. Rust does better: [`acquire`](Pool::acquire) returns a
//! [`PooledConn`] guard, and the connection goes back into the pool in that guard's `Drop`. You
//! literally cannot forget to return it — dropping the guard (at end of scope, on `?`, on panic
//! unwind) returns the connection. This is the same pattern as `MutexGuard`, `File`, and
//! `Box`: ownership + `Drop` make leak-free resource handling the *default*, not a discipline.
//!
//! Two mechanics make it work:
//!   - The guard holds `Option<T>` so its `Drop` can *move* the connection back out (you can't move a
//!     field out of `&mut self` in `Drop` otherwise). `Option::take` leaves `None` behind.
//!   - `Deref`/`DerefMut` let you use the guard as if it were the connection (`conn.query(...)`).
//!
//! # Blocking borrow: `Mutex<VecDeque<T>>` + `Condvar`
//!
//! Free connections live in a `Mutex<VecDeque<T>>`. [`acquire`](Pool::acquire) pops one; if the deque
//! is empty it waits on a `Condvar` until a returning guard `notify_one`s it. This is the textbook
//! wait/notify handoff — always re-check the predicate in a `while` loop after `wait` (spurious
//! wakeups), which is why the code loops rather than `if`s.
//!
//! # Alternatives
//!
//! A counting semaphore instead of a deque; a borrow that returns `Result` after a timeout rather than
//! blocking forever; and, in production, `r2d2` / `deadpool` / `bb8`. This kata is those pools' core
//! idea — RAII checkout — written by hand.

use std::collections::VecDeque;
use std::ops::{Deref, DerefMut};
use std::sync::{Arc, Condvar, Mutex};

struct Inner<T> {
    free: Mutex<VecDeque<T>>,
    available: Condvar,
    capacity: usize,
}

/// A fixed-size pool of reusable `T`s. Cheap to clone (shares one pool) and `Send + Sync`, so hand a
/// clone (or a `&`) to each worker thread.
pub struct Pool<T> {
    inner: Arc<Inner<T>>,
}

impl<T> Clone for Pool<T> {
    fn clone(&self) -> Self {
        Pool {
            inner: Arc::clone(&self.inner),
        }
    }
}

impl<T> Pool<T> {
    /// Build a pool of `capacity` connections, each produced by `factory`.
    ///
    /// Panics if `capacity == 0`.
    pub fn new(capacity: usize, mut factory: impl FnMut() -> T) -> Self {
        assert!(capacity > 0, "pool capacity must be > 0");
        let mut free = VecDeque::with_capacity(capacity);
        for _ in 0..capacity {
            free.push_back(factory());
        }
        Pool {
            inner: Arc::new(Inner {
                free: Mutex::new(free),
                available: Condvar::new(),
                capacity,
            }),
        }
    }

    /// Borrow a connection, blocking until one is free. The connection returns to the pool when the
    /// returned guard is dropped.
    pub fn acquire(&self) -> PooledConn<T> {
        let mut free = self.inner.free.lock().unwrap();
        while free.is_empty() {
            free = self.inner.available.wait(free).unwrap();
        }
        let conn = free.pop_front().unwrap();
        PooledConn {
            conn: Some(conn),
            inner: Arc::clone(&self.inner),
        }
    }

    /// Borrow a connection if one is free right now, else `None` — never blocks.
    pub fn try_acquire(&self) -> Option<PooledConn<T>> {
        let mut free = self.inner.free.lock().unwrap();
        let conn = free.pop_front()?;
        Some(PooledConn {
            conn: Some(conn),
            inner: Arc::clone(&self.inner),
        })
    }

    /// How many connections are free (not currently borrowed) right now.
    pub fn available(&self) -> usize {
        self.inner.free.lock().unwrap().len()
    }

    /// The fixed pool size.
    pub fn capacity(&self) -> usize {
        self.inner.capacity
    }
}

/// An RAII handle to a borrowed connection. Deref to use it; drop it to return it to the pool.
pub struct PooledConn<T> {
    conn: Option<T>,
    inner: Arc<Inner<T>>,
}

impl<T> Deref for PooledConn<T> {
    type Target = T;
    fn deref(&self) -> &T {
        self.conn.as_ref().expect("connection present until drop")
    }
}

impl<T> DerefMut for PooledConn<T> {
    fn deref_mut(&mut self) -> &mut T {
        self.conn.as_mut().expect("connection present until drop")
    }
}

impl<T> Drop for PooledConn<T> {
    fn drop(&mut self) {
        if let Some(conn) = self.conn.take() {
            self.inner.free.lock().unwrap().push_back(conn);
            self.inner.available.notify_one();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Barrier;
    use std::thread;

    #[derive(Debug, Default)]
    struct Conn {
        queries: u32,
    }

    fn int_pool(n: usize) -> Pool<i32> {
        let mut next = 0;
        Pool::new(n, move || {
            next += 1;
            next
        })
    }

    #[test]
    fn new_pool_is_full() {
        let pool = int_pool(3);
        assert_eq!(pool.capacity(), 3);
        assert_eq!(pool.available(), 3);
    }

    #[test]
    fn acquire_borrows_and_drop_returns() {
        let pool = int_pool(2);
        {
            let _a = pool.acquire();
            assert_eq!(pool.available(), 1);
            let _b = pool.acquire();
            assert_eq!(pool.available(), 0);
        } // both guards drop here
        assert_eq!(pool.available(), 2);
    }

    #[test]
    fn guard_derefs_to_the_connection() {
        let pool: Pool<Conn> = Pool::new(1, Conn::default);
        {
            let mut c = pool.acquire();
            c.queries += 1;
            assert_eq!(c.queries, 1);
        }
        // The same connection is reused, carrying its state back.
        let c = pool.acquire();
        assert_eq!(c.queries, 1);
    }

    #[test]
    fn try_acquire_returns_none_when_exhausted() {
        let pool = int_pool(1);
        let held = pool.acquire();
        assert!(pool.try_acquire().is_none());
        drop(held);
        assert!(pool.try_acquire().is_some());
    }

    #[test]
    fn acquire_blocks_until_a_connection_is_returned() {
        let pool = int_pool(1);
        let held = pool.acquire(); // pool now empty

        let pool2 = pool.clone();
        let handle = thread::spawn(move || {
            // Blocks until the main thread returns its connection, then succeeds.
            let _c = pool2.acquire();
        });

        drop(held); // wake the waiter
        handle.join().unwrap(); // completes → blocking + wakeup worked (no hang)
        assert_eq!(pool.available(), 1);
    }

    // Many threads borrow/return at once; the pool must never hand out more than `capacity`
    // connections simultaneously, and every connection must come back. Barrier-gated for contention.
    #[test]
    fn never_over_hands_under_contention() {
        const CAP: usize = 4;
        const THREADS: usize = 8;
        const ITERS: usize = 20_000;

        let pool = int_pool(CAP);
        let in_use = AtomicUsize::new(0);
        let max_seen = AtomicUsize::new(0);
        let barrier = Barrier::new(THREADS);

        thread::scope(|s| {
            for _ in 0..THREADS {
                let pool = &pool;
                let in_use = &in_use;
                let max_seen = &max_seen;
                let barrier = &barrier;
                s.spawn(move || {
                    barrier.wait();
                    for _ in 0..ITERS {
                        let _guard = pool.acquire();
                        let cur = in_use.fetch_add(1, Ordering::SeqCst) + 1;
                        max_seen.fetch_max(cur, Ordering::SeqCst);
                        in_use.fetch_sub(1, Ordering::SeqCst);
                        // _guard drops here, returning the connection
                    }
                });
            }
        });

        assert!(
            max_seen.load(Ordering::SeqCst) <= CAP,
            "handed out more than capacity"
        );
        assert_eq!(pool.available(), CAP); // all returned
    }
}
