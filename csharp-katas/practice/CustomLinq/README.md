# Custom LINQ Operators

Build four deferred sequence operators that complement `System.Linq.Enumerable`.

## The problem

The BCL's LINQ library covers the most common operations, but several useful streaming primitives are missing. You need to implement `Window`, `Batch`, `Scan`, and `Pairwise` — each following the same two-phase deferred-execution contract that LINQ itself uses: validate arguments eagerly, yield elements lazily.

The subtle gotcha is that a method containing `yield return` defers **all** its body — including argument-null checks — until the first `MoveNext`. You must split each operator into a public entry point (validates) and a private iterator (yields) to match LINQ's own behaviour.

## Requirements

- `Window<T>(source, size)` — overlapping sliding windows of exactly `size` elements; if the source is shorter than `size`, yield nothing.
- `Batch<T>(source, size)` — consecutive non-overlapping batches; the final batch may be smaller than `size`.
- `Scan<TSource, TAccumulate>(source, seed, folder)` — emit the running accumulator after every element (inclusive prefix scan); the seed itself is not emitted.
- `Pairwise<T>(source)` — emit `(Previous, Current)` tuples for each consecutive pair; a sequence of 0 or 1 elements yields nothing.
- All operators throw `ArgumentNullException` on null `source` (and `folder` for `Scan`), and `ArgumentOutOfRangeException` for non-positive `size` — **immediately**, not on first iteration.

## What you implement

```csharp
public static IEnumerable<IReadOnlyList<T>> Window<T>(this IEnumerable<T> source, int size)
public static IEnumerable<IReadOnlyList<T>> Batch<T>(this IEnumerable<T> source, int size)
public static IEnumerable<TAccumulate> Scan<TSource, TAccumulate>(
    this IEnumerable<TSource> source,
    TAccumulate seed,
    Func<TAccumulate, TSource, TAccumulate> folder)
public static IEnumerable<(T Previous, T Current)> Pairwise<T>(this IEnumerable<T> source)
```

## The real challenge

The central difficulty is **deferred execution with eager validation**. A naive `yield return` method defers argument checks silently — the test that says `new Action(() => nullSource.Window(3)).Should().Throw<ArgumentNullException>()` will fail because the exception only fires on `MoveNext`. You must separate the public entry point (validates eagerly) from a private iterator method (contains the `yield return`). Getting that split right is the core lesson.

## Run

Write your own tests under `practice.tests/CustomLinq/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~CustomLinq"
```

## Reference

- Worked solution + tests: `solution/CustomLinq/` and `solution.tests/CustomLinq/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/statements/yield
