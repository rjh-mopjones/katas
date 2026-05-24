namespace Katas.AsyncStreams;

/// <summary>
/// Deferred, lazy LINQ-style operators for <see cref="IAsyncEnumerable{T}"/>.
/// </summary>
public static class AsyncSequenceOperators
{
    /// <summary>Projects each element of an async sequence using <paramref name="selector"/>.</summary>
    public static IAsyncEnumerable<TResult> SelectAsync<T, TResult>(
        this IAsyncEnumerable<T> source,
        Func<T, TResult> selector,
        CancellationToken ct = default) =>
        throw new NotImplementedException();

    /// <summary>Filters an async sequence to only elements that satisfy <paramref name="predicate"/>.</summary>
    public static IAsyncEnumerable<T> WhereAsync<T>(
        this IAsyncEnumerable<T> source,
        Func<T, bool> predicate,
        CancellationToken ct = default) =>
        throw new NotImplementedException();

    /// <summary>Returns at most the first <paramref name="count"/> elements from an async sequence.</summary>
    public static IAsyncEnumerable<T> TakeAsync<T>(
        this IAsyncEnumerable<T> source,
        int count,
        CancellationToken ct = default) =>
        throw new NotImplementedException();
}
