namespace Katas.CustomLinq;

/// <summary>
/// Deferred sequence operators that complement the BCL's <see cref="System.Linq.Enumerable"/> class.
/// </summary>
/// <remarks>
/// <para>
/// Every method follows the standard LINQ two-phase pattern: a <c>public</c> entry point
/// validates arguments eagerly (so callers get <see cref="ArgumentNullException"/> /
/// <see cref="ArgumentOutOfRangeException"/> immediately, without waiting for the first
/// <c>MoveNext</c>), then delegates to a <c>private static</c> iterator method that contains
/// the actual <c>yield return</c> logic.  Without this split, argument validation is deferred
/// until the caller first iterates, which is a surprising and hard-to-debug behaviour.
/// </para>
/// <para>
/// <b>Naming note:</b> Several natural names (<c>Chunk</c>, <c>DistinctBy</c>, <c>Zip</c>,
/// <c>Select</c>, etc.) were introduced to <see cref="System.Linq.Enumerable"/> in .NET 6+.
/// We deliberately avoid those names to prevent ambiguous-call compiler errors that arise when
/// two extension methods with identical signatures are both in scope.
/// </para>
/// </remarks>
public static class SequenceOperators
{
    // -------------------------------------------------------------------------
    // Window
    // -------------------------------------------------------------------------

    /// <summary>
    /// Produces overlapping sliding windows of exactly <paramref name="size"/> elements.
    /// </summary>
    /// <typeparam name="T">Element type.</typeparam>
    /// <param name="source">Input sequence.  Must not be <c>null</c>.</param>
    /// <param name="size">Window width.  Must be ≥ 1.</param>
    /// <returns>
    /// A lazily-evaluated sequence of <see cref="IReadOnlyList{T}"/> snapshots.
    /// Each window advances by one element.  If <paramref name="source"/> has fewer
    /// than <paramref name="size"/> elements the result is empty.
    /// </returns>
    /// <exception cref="ArgumentNullException"><paramref name="source"/> is <c>null</c>.</exception>
    /// <exception cref="ArgumentOutOfRangeException"><paramref name="size"/> is ≤ 0.</exception>
    /// <remarks>
    /// <para>
    /// The sliding window is a classic stream-processing primitive.  Use it to compute
    /// moving averages, detect monotonic runs, or produce n-grams from a token stream.
    /// </para>
    /// <para>
    /// <b>Implementation:</b> A <see cref="Queue{T}"/> of capacity <paramref name="size"/> is
    /// maintained.  As each element arrives it is enqueued; once the queue is full the front
    /// element is dequeued before yielding.  This gives O(n·size) time and O(size) space — the
    /// same asymptotic cost as any buffer-based approach.
    /// </para>
    /// <para>
    /// <b>Trade-off:</b> Each yielded list is a fresh <c>T[]</c> snapshot (safe for callers to
    /// keep).  A higher-throughput alternative would yield the same mutable buffer and
    /// document that callers must not retain references, but that design is error-prone.
    /// </para>
    /// </remarks>
    public static IEnumerable<IReadOnlyList<T>> Window<T>(this IEnumerable<T> source, int size)
    {
        if (source is null) throw new ArgumentNullException(nameof(source));
        if (size <= 0) throw new ArgumentOutOfRangeException(nameof(size), size, "Window size must be greater than zero.");
        return WindowIterator(source, size);
    }

    private static IEnumerable<IReadOnlyList<T>> WindowIterator<T>(IEnumerable<T> source, int size)
    {
        var buffer = new Queue<T>(size);
        foreach (var item in source)
        {
            buffer.Enqueue(item);
            if (buffer.Count == size)
            {
                yield return buffer.ToArray();
                buffer.Dequeue();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Batch
    // -------------------------------------------------------------------------

    /// <summary>
    /// Partitions the sequence into consecutive, non-overlapping batches of up to
    /// <paramref name="size"/> elements each.
    /// </summary>
    /// <typeparam name="T">Element type.</typeparam>
    /// <param name="source">Input sequence.  Must not be <c>null</c>.</param>
    /// <param name="size">Maximum batch width.  Must be ≥ 1.</param>
    /// <returns>
    /// A lazily-evaluated sequence of <see cref="IReadOnlyList{T}"/>.  The final
    /// batch may contain fewer than <paramref name="size"/> elements.
    /// </returns>
    /// <exception cref="ArgumentNullException"><paramref name="source"/> is <c>null</c>.</exception>
    /// <exception cref="ArgumentOutOfRangeException"><paramref name="size"/> is ≤ 0.</exception>
    /// <remarks>
    /// <para>
    /// <b>Why "Batch" not "Chunk"?</b>  <see cref="System.Linq.Enumerable.Chunk{TSource}"/> was
    /// added in .NET 6 with the identical signature, so re-using that name causes an
    /// ambiguous-extension-method compile error.
    /// </para>
    /// <para>
    /// <b>Trade-off:</b> Each batch is materialised into a <c>T[]</c> before being yielded.
    /// This is safe (callers may keep the reference) and performs well for moderate batch
    /// sizes.  For very large batches <see cref="ArraySegment{T}"/> over a shared backing
    /// array would reduce allocation pressure, but adds complexity.
    /// </para>
    /// </remarks>
    public static IEnumerable<IReadOnlyList<T>> Batch<T>(this IEnumerable<T> source, int size)
    {
        if (source is null) throw new ArgumentNullException(nameof(source));
        if (size <= 0) throw new ArgumentOutOfRangeException(nameof(size), size, "Batch size must be greater than zero.");
        return BatchIterator(source, size);
    }

    private static IEnumerable<IReadOnlyList<T>> BatchIterator<T>(IEnumerable<T> source, int size)
    {
        var batch = new List<T>(size);
        foreach (var item in source)
        {
            batch.Add(item);
            if (batch.Count == size)
            {
                yield return batch.ToArray();
                batch.Clear();
            }
        }
        if (batch.Count > 0)
            yield return batch.ToArray();
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    /// <summary>
    /// Applies a binary folding function across the sequence, emitting the running
    /// accumulator after each element (also known as a <em>prefix scan</em> or
    /// <em>inclusive scan</em>).
    /// </summary>
    /// <typeparam name="TSource">Input element type.</typeparam>
    /// <typeparam name="TAccumulate">Accumulator type.</typeparam>
    /// <param name="source">Input sequence.  Must not be <c>null</c>.</param>
    /// <param name="seed">Initial accumulator value.  Not emitted itself.</param>
    /// <param name="folder">
    /// A pure function <c>(acc, elem) => newAcc</c>.  Must not be <c>null</c>.
    /// </param>
    /// <returns>
    /// The intermediate accumulator value after each element, yielded in order.
    /// An empty <paramref name="source"/> produces an empty result sequence.
    /// </returns>
    /// <exception cref="ArgumentNullException">
    /// <paramref name="source"/> or <paramref name="folder"/> is <c>null</c>.
    /// </exception>
    /// <remarks>
    /// <para>
    /// <b>Scan vs Aggregate:</b>  <see cref="System.Linq.Enumerable.Aggregate{TSource,TAccumulate}"/>
    /// returns only the <em>final</em> accumulated value.  <c>Scan</c> streams every
    /// intermediate state — useful for running totals, prefix sums, and state-machine
    /// simulations where the full history matters.
    /// </para>
    /// <para>
    /// <b>Why not emit the seed?</b>  This follows the Haskell <c>scanl1</c> / F# <c>Seq.scan</c>
    /// convention where the seed is the invisible starting state, not a data point.
    /// If you need the seed in the output, prepend it yourself via
    /// <c>Enumerable.Repeat(seed, 1).Concat(source.Scan(seed, folder))</c>.
    /// </para>
    /// </remarks>
    public static IEnumerable<TAccumulate> Scan<TSource, TAccumulate>(
        this IEnumerable<TSource> source,
        TAccumulate seed,
        Func<TAccumulate, TSource, TAccumulate> folder)
    {
        if (source is null) throw new ArgumentNullException(nameof(source));
        if (folder is null) throw new ArgumentNullException(nameof(folder));
        return ScanIterator(source, seed, folder);
    }

    private static IEnumerable<TAccumulate> ScanIterator<TSource, TAccumulate>(
        IEnumerable<TSource> source,
        TAccumulate seed,
        Func<TAccumulate, TSource, TAccumulate> folder)
    {
        var acc = seed;
        foreach (var item in source)
        {
            acc = folder(acc, item);
            yield return acc;
        }
    }

    // -------------------------------------------------------------------------
    // Pairwise
    // -------------------------------------------------------------------------

    /// <summary>
    /// Produces consecutive overlapping pairs from the sequence:
    /// <c>(s[0], s[1]), (s[1], s[2]), …, (s[n-2], s[n-1])</c>.
    /// </summary>
    /// <typeparam name="T">Element type.</typeparam>
    /// <param name="source">Input sequence.  Must not be <c>null</c>.</param>
    /// <returns>
    /// A lazily-evaluated sequence of <c>(Previous, Current)</c> value tuples.
    /// A sequence with 0 or 1 elements yields nothing.
    /// </returns>
    /// <exception cref="ArgumentNullException"><paramref name="source"/> is <c>null</c>.</exception>
    /// <remarks>
    /// <para>
    /// <b>Relationship to <c>Window</c>:</b>  <c>Pairwise</c> is equivalent to
    /// <c>Window(2)</c> followed by a projection, but is more expressive for the
    /// common case of detecting adjacent-element relationships (e.g. monotonicity,
    /// delta computation, transition detection).
    /// </para>
    /// <para>
    /// <b>Implementation:</b>  A single <c>hasPrevious</c> flag and one held value are
    /// sufficient — no allocation beyond the iterator state machine itself.
    /// </para>
    /// </remarks>
    public static IEnumerable<(T Previous, T Current)> Pairwise<T>(this IEnumerable<T> source)
    {
        if (source is null) throw new ArgumentNullException(nameof(source));
        return PairwiseIterator(source);
    }

    private static IEnumerable<(T Previous, T Current)> PairwiseIterator<T>(IEnumerable<T> source)
    {
        var hasPrevious = false;
        var previous = default(T)!;
        foreach (var item in source)
        {
            if (hasPrevious)
                yield return (previous, item);
            previous = item;
            hasPrevious = true;
        }
    }
}
