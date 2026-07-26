//! SPSC Ring — a wait-free single-producer / single-consumer queue, built with `unsafe`.
//!
//! # The component
//!
//! The feed-parser thread hands parsed events to the strategy thread over a fixed-capacity ring. One
//! producer, one consumer, no locks, no allocation per item. [`channel`] hands back a [`Producer`] and
//! a [`Consumer`]; move each to its thread.
//!
//! # Why this needs `unsafe`
//!
//! Safe Rust has no way to say "two threads may touch this buffer, and I *promise* they never touch
//! the same slot at the same time" — the borrow checker only understands exclusive-or-shared. A
//! lock-free queue relies exactly on that promise, so the slots live in `UnsafeCell<MaybeUninit<T>>`
//! and we uphold the invariant by hand:
//!
//!   - The producer owns the write index (`tail`) and only ever writes the slot at `tail`.
//!   - The consumer owns the read index (`head`) and only ever reads the slot at `head`.
//!   - `head == tail` means empty; `tail + 1 == head` means full (one slot is always left empty so
//!     the two states are distinguishable without a separate count both threads would contend on).
//!
//! # The ordering that makes it sound
//!
//! The producer writes the slot, then `Release`-stores the new `tail`; the consumer `Acquire`-loads
//! `tail` before reading the slot — so it only reads a slot after the write that filled it
//! *happens-before* the read. Symmetrically, the consumer advances `head` with a `Release` store and
//! the producer `Acquire`-loads `head` before overwriting a slot. Those two release/acquire edges are
//! the whole proof: at any instant a slot has a single accessor, and the hand-off is ordered.
//!
//! # Send / Sync, by hand
//!
//! `UnsafeCell` is `!Sync`, so the ring isn't automatically shareable; we assert `unsafe impl Send +
//! Sync for Ring<T> where T: Send` because the algorithm makes concurrent access safe. And [`Producer`]
//! carries a `PhantomData<Cell<()>>` to make it `!Sync`: you may *move* it to a thread (it is `Send`)
//! but you may not *share* it — the type system now enforces "single producer". Ditto [`Consumer`].
//!
//! # Verify it with Miri
//!
//! Rust has no `-race` flag because safe data races don't compile — but this is `unsafe`, so the tool
//! that plays the role of ThreadSanitizer is **Miri**, which interprets the code and flags UB and data
//! races: `cargo +nightly miri test -p solution spscring` (needs `rustup component add miri`).
//!
//! # Alternative
//!
//! `crossbeam::queue::ArrayQueue` is the production MPMC version; `std::sync::mpsc` is safe but not
//! wait-free. This kata is the C++ `feed_pipe` written in Rust to show what the `unsafe` contract buys
//! and costs. Money angle: a dropped or duplicated event is a missed fill or a phantom position.

use std::cell::{Cell, UnsafeCell};
use std::marker::PhantomData;
use std::mem::MaybeUninit;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

struct Ring<T> {
    buf: Box<[UnsafeCell<MaybeUninit<T>>]>,
    head: AtomicUsize, // next slot to pop (owned by the consumer)
    tail: AtomicUsize, // next slot to push (owned by the producer)
}

// SAFETY: the SPSC discipline (producer only writes `tail`'s slot, consumer only reads `head`'s slot,
// hand-off ordered by release/acquire on head/tail) means concurrent access never aliases a slot.
unsafe impl<T: Send> Send for Ring<T> {}
unsafe impl<T: Send> Sync for Ring<T> {}

impl<T> Ring<T> {
    #[inline]
    fn next(&self, i: usize) -> usize {
        let n = i + 1;
        if n == self.buf.len() {
            0
        } else {
            n
        }
    }
}

impl<T> Drop for Ring<T> {
    fn drop(&mut self) {
        // Single owner here (&mut self): drop every element still in [head, tail).
        let mut head = *self.head.get_mut();
        let tail = *self.tail.get_mut();
        while head != tail {
            // SAFETY: slots in [head, tail) were filled by the producer and never consumed.
            unsafe { (*self.buf[head].get()).assume_init_drop() };
            head = self.next(head);
        }
    }
}

/// The sending half. `Send` (move it to a thread) but `!Sync` (there is exactly one producer).
pub struct Producer<T> {
    ring: Arc<Ring<T>>,
    _not_sync: PhantomData<Cell<()>>,
}

/// The receiving half. `Send` but `!Sync` (there is exactly one consumer).
pub struct Consumer<T> {
    ring: Arc<Ring<T>>,
    _not_sync: PhantomData<Cell<()>>,
}

/// Create a ring holding up to `capacity` items, returning its producer and consumer ends.
///
/// Panics if `capacity == 0`.
pub fn channel<T>(capacity: usize) -> (Producer<T>, Consumer<T>) {
    assert!(capacity > 0, "capacity must be > 0");
    let slots = capacity + 1; // one slot always empty to tell full from empty
    let buf = (0..slots)
        .map(|_| UnsafeCell::new(MaybeUninit::uninit()))
        .collect::<Vec<_>>()
        .into_boxed_slice();
    let ring = Arc::new(Ring {
        buf,
        head: AtomicUsize::new(0),
        tail: AtomicUsize::new(0),
    });
    (
        Producer {
            ring: Arc::clone(&ring),
            _not_sync: PhantomData,
        },
        Consumer {
            ring,
            _not_sync: PhantomData,
        },
    )
}

impl<T> Producer<T> {
    /// Push a value, or hand it back as `Err(value)` if the ring is full. Never blocks.
    pub fn try_push(&self, value: T) -> Result<(), T> {
        let ring = &*self.ring;
        let tail = ring.tail.load(Ordering::Relaxed);
        let next = ring.next(tail);
        if next == ring.head.load(Ordering::Acquire) {
            return Err(value); // full
        }
        // SAFETY: only the producer writes this slot, and the consumer will not read it until we
        // publish `tail` below with a Release store.
        unsafe { (*ring.buf[tail].get()).write(value) };
        ring.tail.store(next, Ordering::Release);
        Ok(())
    }
}

impl<T> Consumer<T> {
    /// Pop the next value, or `None` if the ring is empty. Never blocks.
    pub fn try_pop(&self) -> Option<T> {
        let ring = &*self.ring;
        let head = ring.head.load(Ordering::Relaxed);
        if head == ring.tail.load(Ordering::Acquire) {
            return None; // empty
        }
        // SAFETY: this slot was fully written by the producer before it published the `tail` we just
        // acquire-loaded, and only the consumer reads it. We move the value out exactly once.
        let value = unsafe { (*ring.buf[head].get()).assume_init_read() };
        ring.head.store(ring.next(head), Ordering::Release);
        Some(value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicUsize;
    use std::sync::Barrier;
    use std::thread;

    #[test]
    fn push_pop_is_fifo() {
        let (tx, rx) = channel::<i32>(4);
        assert!(tx.try_push(1).is_ok());
        assert!(tx.try_push(2).is_ok());
        assert_eq!(rx.try_pop(), Some(1));
        assert_eq!(rx.try_pop(), Some(2));
        assert_eq!(rx.try_pop(), None);
    }

    #[test]
    fn full_hands_value_back_then_pop_makes_room() {
        let (tx, rx) = channel::<i32>(2); // holds 2
        assert!(tx.try_push(10).is_ok());
        assert!(tx.try_push(20).is_ok());
        assert_eq!(tx.try_push(30), Err(30)); // full
        assert_eq!(rx.try_pop(), Some(10));
        assert!(tx.try_push(30).is_ok()); // room again
    }

    #[test]
    fn empty_pops_none() {
        let (_tx, rx) = channel::<i32>(4);
        assert_eq!(rx.try_pop(), None);
    }

    #[test]
    fn wraps_around() {
        let (tx, rx) = channel::<i32>(2);
        for round in 0..100 {
            assert!(tx.try_push(round).is_ok());
            assert!(tx.try_push(round + 1000).is_ok());
            assert_eq!(rx.try_pop(), Some(round));
            assert_eq!(rx.try_pop(), Some(round + 1000));
        }
    }

    // Unconsumed elements must be dropped exactly once when the ring is dropped (Miri verifies no
    // leak / double-free). A Drop-counting payload proves the count.
    #[test]
    fn drops_unconsumed_elements_exactly_once() {
        static DROPS: AtomicUsize = AtomicUsize::new(0);
        #[derive(Debug)]
        struct Bomb;
        impl Drop for Bomb {
            fn drop(&mut self) {
                DROPS.fetch_add(1, Ordering::SeqCst);
            }
        }

        DROPS.store(0, Ordering::SeqCst);
        {
            let (tx, rx) = channel::<Bomb>(8);
            tx.try_push(Bomb).unwrap();
            tx.try_push(Bomb).unwrap();
            tx.try_push(Bomb).unwrap();
            drop(rx.try_pop()); // consume + drop one
                                // two remain in the ring; dropping the ends drops the ring, which drops those two.
        }
        assert_eq!(DROPS.load(Ordering::SeqCst), 3); // 1 consumed + 2 unconsumed, none double-dropped
    }

    // One producer thread, one consumer thread, gated to start together. Asserts every item arrives
    // exactly once, in order — no lost, duplicated, or reordered items. Run under Miri for the UB /
    // data-race proof; the FIFO invariant catches logic bugs even without it.
    #[test]
    fn spsc_fifo_under_threads() {
        const N: u64 = 100_000;
        let (tx, rx) = channel::<u64>(1024);
        let barrier = Barrier::new(2);

        thread::scope(|s| {
            let b = &barrier;
            s.spawn(move || {
                b.wait();
                for i in 0..N {
                    while tx.try_push(i).is_err() { /* full: spin until the consumer drains */ }
                }
            });
            let b = &barrier;
            s.spawn(move || {
                b.wait();
                let mut expected = 0u64;
                while expected < N {
                    if let Some(v) = rx.try_pop() {
                        assert_eq!(v, expected);
                        expected += 1;
                    }
                }
            });
        });
    }
}
