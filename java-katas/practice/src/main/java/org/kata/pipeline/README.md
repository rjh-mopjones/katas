# Pipeline

> Build a small fluent transformation pipeline whose public API is a working demonstration of PECS — the same bounded-wildcard reasoning behind `Collections.copy` and `Stream.map`.

## The problem
Implement a generic `Pipeline<T>` that batches elements, transforms them, and drains them elsewhere. The twist is the signatures: each method must accept the *widest* type the operation can safely support — a producer collection should accept any subtype of `T`, a consumer collection should accept any supertype of `T` — so callers can build against a real type hierarchy instead of exact-type matches everywhere.

## Requirements
- `static <T> Pipeline<T> create()` — an empty pipeline of element type `T`.
- `Pipeline<T> addAll(Collection<? extends T> items)` — append every element of `items`; return `this` for chaining.
- `<R> Pipeline<R> map(Function<? super T, ? extends R> fn)` — apply `fn` to every element, returning a **new** pipeline of the mapped type; the receiver is left untouched.
- `void drainTo(Collection<? super T> sink)` — move every element into `sink`, leaving this pipeline empty.
- `List<T> toList()` — a defensive-copy snapshot of the current elements, in order.
- `static <T> void copy(List<? super T> dst, List<? extends T> src)` — append every element of `src` onto `dst` (no instance involved).

## What you implement
Implement `Pipeline<T>` from scratch — the public API is the static `create()`/`copy()` factories plus `addAll`, `map`, `drainTo`, and `toList`. You choose the internal storage and how `map` builds the new pipeline; the wildcard bounds on every signature above must stay exactly as given — get one direction wrong (e.g. `Collection<T>` instead of `Collection<? extends T>`) and the covariance/contravariance tests below won't even compile.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/pipeline/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
