/// A fixed-size pool of worker threads that run submitted closures.
///
/// Give it fields of your own (workers + an `Option<Sender<Job>>`); the tests only touch the public
/// API below.
pub struct ThreadPool;

impl ThreadPool {
    /// Create a pool with `size` worker threads. Panic if `size == 0`.
    pub fn new(_size: usize) -> Self {
        todo!("spawn `size` workers sharing an Arc<Mutex<Receiver<Job>>>; panic if size == 0")
    }

    /// Submit a closure to run on some worker thread. Keep these bounds verbatim.
    pub fn execute<F>(&self, _f: F)
    where
        F: FnOnce() + Send + 'static,
    {
        todo!("box the closure and send it down the channel")
    }
}

// Graceful shutdown lives in `Drop`: drop the sender so `recv` errors, then join every worker.
// impl Drop for ThreadPool { ... }
