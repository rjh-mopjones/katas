# Iterators

Implement C# iterator patterns at two levels: by hand and via `yield return`.

## The problem

`CountdownSequence` asks you to build the state machine that the C# compiler generates for `yield return` — by hand. You implement `IEnumerable<int>` and `IEnumerator<int>` on the same class, manage a `_state` field, and make `GetEnumerator` return `this` on the first call (allocation optimisation) but a fresh instance on subsequent calls. `Sequences` is the other side of the coin: you use `yield return` to write infinite sequences, and the compiler does the work.

## Requirements

**CountdownSequence:**
- Counts from `From` down to 1; if `From < 1` the sequence is empty.
- `GetEnumerator` returns `this` on the first call (state machine is already armed); returns a new armed instance on every subsequent call.
- `MoveNext` returns `false` when the sequence is exhausted or `Dispose` has been called.
- `Reset` re-arms the sequence so it can be iterated again from the start.
- `Dispose` marks the enumerator as finished (idempotent).

**Sequences:**
- `Fibonacci()` — infinite `long` sequence: 0, 1, 1, 2, 3, 5, …
- `Naturals()` — infinite `int` sequence: 1, 2, 3, …
- `Cycle(items)` — infinitely repeats `items` in order; throws `ArgumentNullException` for null and `ArgumentException` for empty.

## What you implement

```csharp
// CountdownSequence
public sealed class CountdownSequence : IEnumerable<int>, IEnumerator<int>
public CountdownSequence(int from)
public IEnumerator<int> GetEnumerator()
public int Current { get; }
public bool MoveNext()
public void Reset()
public void Dispose()

// Sequences
public static IEnumerable<long> Fibonacci()
public static IEnumerable<int> Naturals()
public static IEnumerable<T> Cycle<T>(IReadOnlyList<T> items)
```

## The real challenge

`CountdownSequence` is the hard part. You must understand the compiler's `yield` state machine well enough to replicate it manually: the dual-interface trick (same object = `IEnumerable` + `IEnumerator`), sentinel state values (`-2` = not started/disposed, `-1` = finished), and the `GetEnumerator` optimisation that returns `this` only when unarmed. Getting `Reset` and `Dispose` semantics right (and making `MoveNext` safe to call in all states) is the real challenge. For `Sequences`, the gotcha is that `Cycle`'s eager validation must not be deferred — you need the two-phase split.

## Run

Write your own tests under `practice.tests/Iterators/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Iterators"
```

## Reference

- Worked solution + tests: `solution/Iterators/` and `solution.tests/Iterators/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/statements/yield
