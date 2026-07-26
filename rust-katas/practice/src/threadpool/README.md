# Thread Pool

> A bounded set of worker threads draining a shared job queue — submit closures, they run on a fixed number of threads, and the pool shuts down cleanly when it drops.

## The problem

Spawning one thread per task lets a burst of work exhaust memory or thrash the scheduler. A thread
pool caps concurrency: create `size` worker threads once, then feed them closures. Each job runs on
whichever worker is free. This is the Rust Book's capstone project — the interesting part is not the
happy path but stopping cleanly without losing queued work or leaking threads.

## Requirements

- `ThreadPool::new(size)` spawns exactly `size` worker threads; it panics if `size == 0`.
- `execute(f)` hands the closure to some worker; it runs exactly once, on a worker thread.
- Workers share **one** job queue — a job runs on whatever worker dequeues it.
- When the pool is dropped, every already-submitted job still runs, every worker thread is joined,
  and nothing panics or leaks. No sentinel/"poison" message.

## What you implement

- `fn new(size: usize) -> Self` — spawn the workers, wire up the channel.
- `fn execute<F>(&self, f: F) where F: FnOnce() + Send + 'static` — box and enqueue the job (keep the
  bounds verbatim).
- `impl Drop for ThreadPool` — the graceful shutdown; not in the skeleton, you add it.

## The real challenge

- **The job type is `Box<dyn FnOnce() + Send + 'static>`, and every bound earns its place.** `FnOnce`:
  a job runs once and may consume its captures. `Send`: it is created on the caller's thread and runs
  on a worker, so it must cross the thread boundary. `'static`: it may run long after the caller's
  frame is gone, so it may not borrow the caller's locals. `Box<dyn …>`: each closure has a distinct
  compiler-generated type, so you erase it behind an unsized trait object and heap-allocate it.
- **Share the receiver with `Arc<Mutex<Receiver<Job>>>`.** An `mpsc::Receiver` is neither `Clone` nor
  `Sync`, so you can't give each worker its own copy. Wrap the single receiver in a `Mutex` (one
  worker dequeues at a time) inside an `Arc` (shared ownership). Drop the `MutexGuard` **before**
  running the job, so the lock covers only the dequeue, not the work.
- **Graceful shutdown is the trap.** A worker blocked in `recv()` only wakes when a job arrives or the
  channel closes. Store the `Sender` as `Option<Sender<Job>>`; in `Drop`, `take()` it so the channel
  closes, then `recv()` returns `Err` and each worker breaks out of its loop. Store each `JoinHandle`
  as `Option<JoinHandle<()>>` and `take()` it in `Drop` to `join()` every worker — the drop blocks
  until all work is done.
- **No lost jobs.** `recv` yields the buffered jobs before it errors, so dropping the sender drains
  the queue first — every submitted job runs before its worker exits.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` in this
file. Make the tests **deterministic** (no `sleep` to synchronise): submit N jobs that increment a
shared `Arc<AtomicUsize>` inside an inner scope `{ let pool = ...; ... }`, then assert the counter
after the scope ends (the pool's `Drop` joins all workers first); or send results back over an
`mpsc::channel` and `recv` N of them. Then:

```
cd rust-katas && cargo test -p practice threadpool
```

## Reference

Worked solution: `rust-katas/solution/src/threadpool/`.

Extension: replace the channel with your own queue — a `Mutex<VecDeque<Job>>` + `Condvar` (workers
`wait` on the condvar, `execute` pushes and `notify_one`, `Drop` sets a "shutting down" flag and
`notify_all`). Or add a `join()` that waits for the current backlog to drain, or return a result
future from `execute`.

Background: [The Rust Book — Building a Multithreaded Web Server (Final Project)](https://doc.rust-lang.org/book/ch21-02-multithreaded.html).
