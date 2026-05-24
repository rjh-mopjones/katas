namespace Katas.Channels;

/// <summary>
/// Demonstrates a producer/consumer pipeline using <see cref="BoundedQueue{T}"/>.
/// </summary>
public static class Pipeline
{
    /// <summary>
    /// Starts <paramref name="producerCount"/> concurrent producers that each write
    /// <paramref name="itemsPerProducer"/> integers (0 to itemsPerProducer-1) into a shared
    /// bounded channel, then consumes and returns the sum of all produced values.
    /// </summary>
    /// <param name="producerCount">Number of concurrent producer tasks.</param>
    /// <param name="itemsPerProducer">Items written by each producer.</param>
    /// <param name="capacity">Bounded channel capacity; governs backpressure.</param>
    /// <param name="ct">Cancellation token propagated to writers and the consumer.</param>
    /// <returns>The sum of all integers produced across all producers.</returns>
    public static Task<long> SumProducedAsync(
        int producerCount,
        int itemsPerProducer,
        int capacity,
        CancellationToken ct = default) =>
        throw new NotImplementedException();
}
