use std::marker::PhantomData;
use std::ops::{Deref, DerefMut};

/// A fixed-size pool of reusable `T`s. You design the internals (shared free-list + blocking).
pub struct Pool<T> {
    _marker: PhantomData<T>,
}

impl<T> Clone for Pool<T> {
    fn clone(&self) -> Self {
        todo!()
    }
}

impl<T> Pool<T> {
    pub fn new(_capacity: usize, _factory: impl FnMut() -> T) -> Self {
        todo!()
    }

    pub fn acquire(&self) -> PooledConn<T> {
        todo!()
    }

    pub fn try_acquire(&self) -> Option<PooledConn<T>> {
        todo!()
    }

    pub fn available(&self) -> usize {
        todo!()
    }

    pub fn capacity(&self) -> usize {
        todo!()
    }
}

/// An RAII handle to a borrowed connection. Deref to use it; its `Drop` must return it to the pool.
pub struct PooledConn<T> {
    _marker: PhantomData<T>,
}

impl<T> Deref for PooledConn<T> {
    type Target = T;
    fn deref(&self) -> &T {
        todo!()
    }
}

impl<T> DerefMut for PooledConn<T> {
    fn deref_mut(&mut self) -> &mut T {
        todo!()
    }
}
