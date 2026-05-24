namespace Katas.CustomLinq;

/// <summary>
/// Deferred sequence operators that complement <see cref="System.Linq.Enumerable"/>.
/// </summary>
public static class SequenceOperators
{
    /// <summary>Produces overlapping sliding windows of exactly <paramref name="size"/> elements.</summary>
    public static IEnumerable<IReadOnlyList<T>> Window<T>(this IEnumerable<T> source, int size) =>
        throw new NotImplementedException();

    /// <summary>Partitions the sequence into consecutive, non-overlapping batches of up to <paramref name="size"/> elements.</summary>
    public static IEnumerable<IReadOnlyList<T>> Batch<T>(this IEnumerable<T> source, int size) =>
        throw new NotImplementedException();

    /// <summary>Applies a binary folding function across the sequence, emitting the running accumulator after each element.</summary>
    public static IEnumerable<TAccumulate> Scan<TSource, TAccumulate>(
        this IEnumerable<TSource> source,
        TAccumulate seed,
        Func<TAccumulate, TSource, TAccumulate> folder) =>
        throw new NotImplementedException();

    /// <summary>Produces consecutive overlapping pairs: (s[0],s[1]), (s[1],s[2]), …</summary>
    public static IEnumerable<(T Previous, T Current)> Pairwise<T>(this IEnumerable<T> source) =>
        throw new NotImplementedException();
}
