namespace Katas.AsyncStreams;

/// <summary>
/// Produces a sequence of integers as an async stream.
/// </summary>
public sealed class NumberSource
{
    /// <summary>
    /// Produces integers <c>0</c> through <c>count - 1</c> asynchronously, one per iteration.
    /// </summary>
    /// <param name="count">Number of items to produce. Must be non-negative.</param>
    /// <param name="ct">Cancellation token. Checked before each item is yielded.</param>
    /// <returns>A deferred async sequence; no work starts until the caller begins iterating.</returns>
    /// <exception cref="ArgumentOutOfRangeException">Thrown if <paramref name="count"/> is negative.</exception>
    public IAsyncEnumerable<int> ProduceAsync(
        int count,
        CancellationToken ct = default) =>
        throw new NotImplementedException();
}
