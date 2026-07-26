use std::marker::PhantomData;

/// The sending half. You design the internals (an `UnsafeCell` ring + atomic indices).
pub struct Producer<T> {
    _marker: PhantomData<T>,
}

/// The receiving half.
pub struct Consumer<T> {
    _marker: PhantomData<T>,
}

/// Create a ring holding up to `capacity` items, returning its producer and consumer ends.
pub fn channel<T>(_capacity: usize) -> (Producer<T>, Consumer<T>) {
    todo!("build the ring and split it into a producer and consumer")
}

impl<T> Producer<T> {
    /// Push a value, or hand it back as `Err(value)` if the ring is full. Never blocks.
    pub fn try_push(&self, _value: T) -> Result<(), T> {
        todo!()
    }
}

impl<T> Consumer<T> {
    /// Pop the next value, or `None` if the ring is empty. Never blocks.
    pub fn try_pop(&self) -> Option<T> {
        todo!()
    }
}
