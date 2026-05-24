namespace Katas.Channels;

/// <summary>
/// Demonstrates a producer/consumer pipeline using <see cref="BoundedQueue{T}"/>.
/// </summary>
public static class Pipeline
{
    /// <summary>
    /// Starts <paramref name="producerCount"/> concurrent producers that each write
    /// <paramref name="itemsPerProducer"/> integers into a shared bounded channel, then
    /// consumes the channel and returns the sum of all produced values.
    /// </summary>
    /// <param name="producerCount">Number of concurrent producer tasks.</param>
    /// <param name="itemsPerProducer">Items written by each producer (values 0 to itemsPerProducer-1).</param>
    /// <param name="capacity">Bounded channel capacity; governs backpressure.</param>
    /// <param name="ct">Cancellation token propagated to writers and the consumer.</param>
    /// <returns>The sum of all integers produced across all producers.</returns>
    /// <remarks>
    /// <para>
    /// <b>Backpressure in practice:</b> With <c>capacity = 1</c> and two producers, the
    /// second producer's <c>WriteAsync</c> blocks until the consumer reads the first item.
    /// This demonstrates that the channel capacity is not just advisory — writes literally
    /// suspend at the <c>ValueTask</c> level until space is available.
    /// </para>
    /// <para>
    /// <b>Completion handoff:</b> <c>Task.WhenAll(producers)</c> ensures all writers have
    /// finished before calling <c>Complete()</c>. If <c>Complete()</c> were called too early
    /// (e.g. after starting but before all writers finish) the consumer loop would terminate
    /// prematurely and some items would be lost.
    /// </para>
    /// <para>
    /// <b>Why <c>long</c> sum?</b> With many producers and large item counts the sum can
    /// easily exceed <c>int.MaxValue</c>.
    /// </para>
    /// </remarks>
    public static async Task<long> SumProducedAsync(
        int producerCount,
        int itemsPerProducer,
        int capacity,
        CancellationToken ct = default)
    {
        var queue = new BoundedQueue<int>(capacity);

        // Start all producers concurrently.
        Task[] producers = Enumerable
            .Range(0, producerCount)
            .Select(_ => ProduceAsync(queue, itemsPerProducer, ct))
            .ToArray();

        // Await all producers, then signal completion.
        // We do not await inline to let producers run concurrently with the consumer.
        Task completionSignal = Task.WhenAll(producers).ContinueWith(
            _ => queue.Complete(),
            CancellationToken.None,
            TaskContinuationOptions.ExecuteSynchronously,
            TaskScheduler.Default);

        // Consume all items and sum.
        long sum = 0;
        await foreach (int item in queue.ReadAllAsync(ct).ConfigureAwait(false))
            sum += item;

        // Propagate any producer exceptions.
        await completionSignal.ConfigureAwait(false);

        return sum;
    }

    private static async Task ProduceAsync(BoundedQueue<int> queue, int count, CancellationToken ct)
    {
        for (int i = 0; i < count; i++)
            await queue.WriteAsync(i, ct).ConfigureAwait(false);
    }
}
