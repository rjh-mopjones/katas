# Pipeline

## Approach
`Pipeline<T>` wraps a single invariant `List<T>` — read and written internally, so no wildcard alone
would be sound there — and exposes four operations whose signatures are the actual point of the kata.
`addAll(Collection<? extends T> items)` and the `src` side of the static `copy` only ever *read* their
argument, which is exactly what licenses `? extends T` (a producer): a `List<Integer>` can be read into
a `Pipeline<Number>`, at the cost of not being able to write into that argument (the compiler can't
prove which subtype it actually holds). `drainTo(Collection<? super T> sink)` and the `dst` side of
`copy` only ever *write into* their argument, licensing `? super T` (a consumer): a `Pipeline<Number>`
can drain into a `List<Object>`, at the cost of only being able to read `Object` back out.

`map`'s `Function<? super T, ? extends R>` applies the same PECS reasoning to a function instead of a
collection: the function consumes `T` (so it must accept at least `T`) and produces `R` (so it must
yield at most `R`) — the identical bound the JDK puts on `Stream.map`.

The static `create()` factory exists so `T` can be inferred at the call site (or written explicitly as
`Pipeline.<Integer>create()`) without the private no-arg constructor being reachable directly. Backing
storage is a plain `List`, sidestepping the fact that `new T[n]` doesn't compile under erasure — the
JVM has nothing reified to allocate at that array-creation site; `ArrayList` already hides its own
`Object[]` behind a type-safe API, which is why it's the idiomatic real-world choice over hand-rolling
an array-backed structure here.

## The real challenge
- **Producer-extends**: `addAll` and the `src` side of `copy` only ever *read* from their argument — that's what licenses `? extends T`, and it's also what forbids writing into that argument (the compiler can't prove which subtype it actually holds).
- **Consumer-super**: `drainTo` and the `dst` side of `copy` only ever *write into* their argument — that's what licenses `? super T`, and it's also what limits reading back out of it to `Object`.
- **The invariant middle**: the backing storage for the pipeline's own elements should stay a plain `List<T>` — it's both read and written internally, so no wildcard alone would be sound there.
- **Erasure**: you can't `new T[n]` — the JVM has nothing to reify at that array-creation site. Decide how that constrains your internal storage choice, and be ready to explain why a generic varargs factory (`T... items`) would need `@SafeVarargs` to compile cleanly.

## Common mistakes & senior signal
- Writing the "obvious" unbounded signature (`Collection<T>`) instead of the bounded one — it still
  compiles and passes a same-type test, but silently rejects the covariant/contravariant call sites
  the kata is testing for.
- Getting `extends`/`super` backwards — putting `? super T` on a producer parameter or `? extends T`
  on a consumer parameter compiles in isolation but breaks the intended call sites.
- Making the internal `List<T>` field itself a wildcard type — it's both read and written internally,
  so it must stay invariant.
- Trying `new T[n]` for internal storage and being surprised it doesn't compile — the fix is to back
  the structure with a `List`, not to fight erasure with an unchecked array cast.
- Not being able to explain *why* `@SafeVarargs` is a promise to the compiler, not a fix, if asked
  about a hypothetical varargs factory.

## Extensions
- **Recursive generic bounds** — a `sorted()` operation would need `<T extends Comparable<? super T>>`
  on the class or method: `T` must be comparable to itself or one of its own supertypes, the same
  bound `Collections.max` uses.
- **`@SafeVarargs` factory** — add `static <T> Pipeline<T> of(T... items)` once the varargs body is
  verified never to write into the shared array, so callers can write `Pipeline.of(1, 2, 3)` instead
  of `addAll(List.of(1, 2, 3))`.
- **Lazy/streaming pipeline** — replace the eager `List<T>` with a `Stream`-backed implementation so
  `map` composes functions instead of materialising an intermediate list at every stage.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/pipeline/`)
- Java Interview Primer: generics / bounded wildcards / PECS
