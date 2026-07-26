//! Thread Pool — a fixed-size pool of worker threads draining a shared job queue.
//!
//! # The component
//!
//! The Rust Book's capstone: a [`ThreadPool`] owns a fixed number of OS threads and hands each
//! submitted closure to whichever worker is free. `execute` enqueues a job; the workers run them.
//! Bounding the thread count is the whole point — spawning one thread per task lets a burst of work
//! exhaust memory or thrash the scheduler, so a pool caps concurrency at a number you chose.
//!
//! # Why a job is `Box<dyn FnOnce() + Send + 'static>`
//!
//! Each field of that type earns its keep:
//!
//! - **`FnOnce`** — a job runs exactly once, so the weakest call trait is the right one. It also lets
//!   a closure *consume* the values it captured (move them out), which `Fn`/`FnMut` forbid.
//! - **`Send`** — the closure is created on the caller's thread and executed on a worker thread, so it
//!   must be safe to transfer ownership across the thread boundary.
//! - **`'static`** — the job may run at any later time, after the caller's stack frame is long gone, so
//!   it may not borrow the caller's locals; it must own everything it touches (or borrow `'static`).
//! - **`Box<dyn …>`** — every closure has a distinct, compiler-generated type, so we can't name one
//!   concrete type for the channel. We erase it behind `dyn FnOnce`, and because a trait object is
//!   unsized we put it on the heap in a `Box`. [`Job`] is that boxed trait object.
//!
//! # Why `Arc<Mutex<Receiver<Job>>>`
//!
//! Jobs travel over an [`mpsc`] channel: the pool holds the single [`Sender`], every worker shares the
//! one [`Receiver`]. But `mpsc::Receiver` is *not* `Clone` and not `Sync` — you cannot hand a copy to
//! each thread. So the workers share **one** receiver wrapped in a [`Mutex`] (only one thread reads at a
//! time) inside an [`Arc`] (multiple owners, one heap allocation, refcounted). A worker locks the
//! mutex, calls `recv`, and — importantly — the [`MutexGuard`] is dropped before the job runs, so the
//! lock is held only for the dequeue, not for the (arbitrarily long) work.
//!
//! The cost: because only one worker holds the lock at a time, dequeuing is serialized — fine when jobs
//! dwarf the lock hold, a throughput ceiling when they don't. A production pool replaces this with a
//! lock-free MPMC queue (`crossbeam-channel`) or a per-worker work-stealing deque (`rayon`), so every
//! worker pulls work without contending on one mutex.
//!
//! # Graceful shutdown on `Drop`
//!
//! The subtle part is *stopping*. A worker blocked in `recv()` only wakes when a job arrives — or when
//! the channel closes. `recv()` returns `Err` once **all** senders are dropped. So [`ThreadPool`] keeps
//! its sender in an `Option`, and `Drop` runs the choreography:
//!
//! 1. `self.sender.take()` — drop the only sender. Now the channel is closed.
//! 2. Each idle worker's `recv()` returns `Err`; the worker breaks its loop and its thread exits. Jobs
//!    already queued are still drained first — `recv` yields the buffered jobs before it errors, so **no
//!    submitted job is lost**.
//! 3. `join()` every worker handle (also stored in an `Option` so `Drop` can `take` it) — the drop
//!    blocks until every worker has finished, so all in-flight work completes before the pool goes away.
//!
//! This is the clean-shutdown pattern: no sentinel "poison" message, no panic, no detached threads
//! leaking past the pool's lifetime.
//!
//! # Alternatives
//!
//! `rayon` for data-parallel work-stealing; `crossbeam-channel` for a clonable MPMC queue that removes
//! the shared-mutex bottleneck; a hand-rolled `Mutex<VecDeque<Job>>` + `Condvar` if you want the queue
//! without a channel. This kata builds the `Arc<Mutex<Receiver>>` version by hand to make the ownership
//! and shutdown explicit.

use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

/// A boxed, type-erased unit of work. Heap-allocated because `dyn FnOnce` is unsized; `Send + 'static`
/// because it crosses to a worker thread and outlives the caller's stack frame.
type Job = Box<dyn FnOnce() + Send + 'static>;

/// A fixed-size pool of worker threads that run submitted closures.
pub struct ThreadPool {
    workers: Vec<Worker>,
    /// `Option` so `Drop` can `take` (and thus drop) the sender, closing the channel to signal shutdown.
    sender: Option<Sender<Job>>,
}

impl ThreadPool {
    /// Create a pool with `size` worker threads.
    ///
    /// # Panics
    ///
    /// Panics if `size` is 0 — a pool with no workers could never run a job, so it is a programmer
    /// error rather than a runtime condition to handle.
    pub fn new(size: usize) -> Self {
        assert!(size > 0, "thread pool size must be greater than zero");

        let (sender, receiver) = mpsc::channel::<Job>();
        // One receiver shared by every worker: Arc for shared ownership, Mutex because the receiver
        // is not Sync and only one worker may dequeue at a time.
        let receiver = Arc::new(Mutex::new(receiver));

        let mut workers = Vec::with_capacity(size);
        for id in 0..size {
            workers.push(Worker::new(id, Arc::clone(&receiver)));
        }

        ThreadPool {
            workers,
            sender: Some(sender),
        }
    }

    /// Submit a closure to run on some worker thread.
    ///
    /// The bounds mirror [`Job`]: `FnOnce` (runs once, may consume its captures), `Send` (moves to a
    /// worker thread), `'static` (may run after the caller's frame is gone, so it owns its captures).
    pub fn execute<F>(&self, f: F)
    where
        F: FnOnce() + Send + 'static,
    {
        let job: Job = Box::new(f);
        // The sender is present for the whole life of the pool (only `Drop` takes it), so the send
        // cannot fail here; `expect` documents that invariant.
        self.sender
            .as_ref()
            .expect("sender is present until Drop")
            .send(job)
            .expect("workers outlive every execute call");
    }
}

impl Drop for ThreadPool {
    fn drop(&mut self) {
        // 1. Drop the sender: closes the channel so each worker's `recv` returns `Err` and it exits.
        drop(self.sender.take());

        // 2. Join every worker so all queued and in-flight jobs finish before the pool goes away.
        for worker in &mut self.workers {
            if let Some(handle) = worker.handle.take() {
                handle.join().expect("worker thread panicked");
            }
        }
    }
}

/// One worker thread and its join handle.
struct Worker {
    _id: usize,
    /// `Option` so `Drop` can `take` the handle out (`join` consumes it) while iterating by `&mut`.
    handle: Option<JoinHandle<()>>,
}

impl Worker {
    fn new(id: usize, receiver: Arc<Mutex<Receiver<Job>>>) -> Self {
        let handle = thread::spawn(move || {
            loop {
                // Lock, receive, then release the guard *before* running the job so the lock only
                // covers the dequeue — not the (arbitrarily long) work.
                let message = receiver.lock().expect("receiver mutex poisoned").recv();
                match message {
                    Ok(job) => job(),
                    // All senders dropped -> channel closed -> shut this worker down.
                    Err(_) => break,
                }
            }
        });

        Worker {
            _id: id,
            handle: Some(handle),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    #[test]
    fn runs_every_submitted_job() {
        let counter = Arc::new(AtomicUsize::new(0));
        const N: usize = 1000;

        {
            let pool = ThreadPool::new(4);
            for _ in 0..N {
                let c = Arc::clone(&counter);
                pool.execute(move || {
                    c.fetch_add(1, Ordering::SeqCst);
                });
            }
            // Pool drops at end of scope: Drop joins every worker, so all N jobs have finished
            // before the assertion below runs. No sleep needed for synchronisation.
        }

        assert_eq!(counter.load(Ordering::SeqCst), N);
    }

    #[test]
    fn collects_results_over_a_channel() {
        const N: usize = 100;
        let (tx, rx) = mpsc::channel::<usize>();

        {
            let pool = ThreadPool::new(4);
            for i in 0..N {
                let tx = tx.clone();
                pool.execute(move || {
                    tx.send(i * i).expect("test receiver alive");
                });
            }
        }
        drop(tx); // drop the last sender so the receiver iterator would end (not required for recv N)

        let mut results: Vec<usize> = (0..N)
            .map(|_| rx.recv().expect("a result per job"))
            .collect();
        results.sort_unstable();

        let expected: Vec<usize> = (0..N).map(|i| i * i).collect();
        assert_eq!(results, expected);
    }

    #[test]
    fn a_single_worker_runs_many_jobs() {
        let counter = Arc::new(AtomicUsize::new(0));
        const N: usize = 50;

        {
            let pool = ThreadPool::new(1);
            for _ in 0..N {
                let c = Arc::clone(&counter);
                pool.execute(move || {
                    c.fetch_add(1, Ordering::SeqCst);
                });
            }
        }

        assert_eq!(counter.load(Ordering::SeqCst), N);
    }

    #[test]
    #[should_panic(expected = "greater than zero")]
    fn new_with_zero_panics() {
        let _ = ThreadPool::new(0);
    }
}
