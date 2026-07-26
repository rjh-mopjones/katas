//! Rust katas — reference solutions.
//!
//! Each module is one kata: a worked implementation with an interview-grade `//!` doc explaining the
//! trap, the chosen construct, the trade-offs, and named alternatives, plus a `#[cfg(test)]` suite
//! that pins the contract. Half are components of a low-latency trading platform; half are the
//! canonical "implement it from scratch" senior-Rust asks (LRU, thread pool, expression evaluator,
//! pub/sub dispatch). The `practice/` twin is these same modules reduced to `todo!()` skeletons.

pub mod calc;
pub mod candles;
pub mod eventbus;
pub mod lru;
pub mod orderstate;
pub mod positionbook;
pub mod spscring;
pub mod threadpool;
pub mod tickparser;
