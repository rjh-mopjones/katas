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

## The real challenge
- **Producer-extends**: `addAll` and the `src` side of `copy` only ever *read* from their argument — that's what licenses `? extends T`, and it's also what forbids writing into that argument (the compiler can't prove which subtype it actually holds).
- **Consumer-super**: `drainTo` and the `dst` side of `copy` only ever *write into* their argument — that's what licenses `? super T`, and it's also what limits reading back out of it to `Object`.
- **The invariant middle**: the backing storage for the pipeline's own elements should stay a plain `List<T>` — it's both read and written internally, so no wildcard alone would be sound there.
- **Erasure**: you can't `new T[n]` — the JVM has nothing to reify at that array-creation site. Decide how that constrains your internal storage choice, and be ready to explain why a generic varargs factory (`T... items`) would need `@SafeVarargs` to compile cleanly.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/pipeline/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/pipeline/`
- Java Interview Primer: generics / bounded wildcards / PECS
