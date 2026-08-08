# Pipeline

> Build a small fluent transformation pipeline whose public API is a working demonstration of PECS — the same bounded-wildcard reasoning behind `Collections.copy` and `Stream.map`.

## The problem
Implement a generic pipeline over an element type `T` that batches elements, transforms them, and drains them elsewhere. The twist is the signatures: each operation must accept the *widest* type it can safely support — a producer collection should accept any subtype of the element type, a consumer collection should accept any supertype of it — so callers can build against a real type hierarchy instead of exact-type matches everywhere.

## Requirements
- An empty pipeline can be created for any element type.
- Bulk-adding elements accepts a producer collection of that element type *or any of its subtypes*, and returns the pipeline itself so calls can chain.
- Mapping applies a transform to every element and returns a **new** pipeline of the mapped type, leaving the original untouched; the transform function itself should accept any supertype of the element type and may produce any subtype of the target type.
- Draining moves every element into a consumer collection that accepts the element type *or any of its supertypes*, leaving the pipeline empty afterwards.
- Taking a snapshot of the current elements returns a defensive copy, in order.
- A standalone copy operation appends every element of one list onto another, following the same producer/consumer variance direction as above, with no pipeline instance involved.
- Getting the variance direction backwards anywhere (producer vs. consumer) should be a compile error, not a runtime one — this is the actual point of the exercise.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/pipeline/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
