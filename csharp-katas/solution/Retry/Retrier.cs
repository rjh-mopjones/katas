namespace Katas.Retry;

/// <summary>
/// Executes an async operation, retrying on failure according to a <see cref="RetryPolicy"/>.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why inject <see cref="TimeProvider"/>?</b> Real-time sleeping makes tests slow and
/// non-deterministic. By accepting a <see cref="TimeProvider"/> the caller can substitute
/// <c>Microsoft.Extensions.Time.Testing.FakeTimeProvider</c> in tests to advance virtual
/// time without any real wall-clock delay.
/// </para>
/// <para>
/// <b>Why inject the jitter source?</b> <c>Random.Shared.NextDouble()</c> is non-deterministic,
/// making retry-delay assertions flaky. Injecting a <c>Func&lt;double&gt;</c> lets tests
/// supply a fixed value (e.g. <c>() =&gt; 0.5</c>) for fully reproducible assertions.
/// </para>
/// <para>
/// <b>Exception semantics:</b> Every attempt's exception is discarded except the last one,
/// which is re-thrown as-is (no wrapping). This preserves the original stack trace.
/// If you need aggregate exception information, wrap this and collect exceptions per attempt.
/// </para>
/// </remarks>
public sealed class Retrier
{
    private readonly RetryPolicy _policy;
    private readonly TimeProvider _timeProvider;
    private readonly Func<double> _jitterSource;

    /// <summary>
    /// Initialises a new <see cref="Retrier"/>.
    /// </summary>
    /// <param name="policy">Retry configuration.</param>
    /// <param name="timeProvider">
    /// Time provider used for <c>Task.Delay</c>. Defaults to <see cref="TimeProvider.System"/>.
    /// </param>
    /// <param name="jitterSource">
    /// Returns a value in <c>[0, 1)</c> used as the jitter fraction. Defaults to
    /// <c>Random.Shared.NextDouble</c>.
    /// </param>
    public Retrier(
        RetryPolicy policy,
        TimeProvider? timeProvider = null,
        Func<double>? jitterSource = null)
    {
        _policy = policy;
        _timeProvider = timeProvider ?? TimeProvider.System;
        _jitterSource = jitterSource ?? Random.Shared.NextDouble;
    }

    /// <summary>
    /// Executes <paramref name="action"/>, retrying on exception up to
    /// <see cref="RetryPolicy.MaxAttempts"/> times total.
    /// </summary>
    /// <typeparam name="T">Return type of the action.</typeparam>
    /// <param name="action">
    /// The operation to execute. Receives the <see cref="CancellationToken"/> so it can
    /// observe cancellation without a separate overload.
    /// </param>
    /// <param name="ct">Cancellation token. Passed through to <paramref name="action"/> and to
    /// <c>Task.Delay</c> so cancellation interrupts both the operation and any inter-attempt sleep.
    /// </param>
    /// <returns>The return value of the first successful invocation of <paramref name="action"/>.</returns>
    /// <exception cref="Exception">
    /// The exception thrown by the final attempt once <see cref="RetryPolicy.MaxAttempts"/>
    /// has been exhausted.
    /// </exception>
    /// <remarks>
    /// <b>No delay after the final attempt:</b> once <see cref="RetryPolicy.MaxAttempts"/> is
    /// exhausted the exception is re-thrown immediately. Delaying at that point would increase
    /// perceived latency for the caller with no benefit.
    /// </remarks>
    public async Task<T> ExecuteAsync<T>(
        Func<CancellationToken, Task<T>> action,
        CancellationToken ct = default)
    {
        Exception? lastException = null;

        for (int attempt = 1; attempt <= _policy.MaxAttempts; attempt++)
        {
            try
            {
                return await action(ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                // Do not retry genuine cancellations; propagate immediately.
                throw;
            }
            catch (Exception ex)
            {
                lastException = ex;

                bool isLastAttempt = attempt == _policy.MaxAttempts;
                if (isLastAttempt)
                    break;

                TimeSpan delay = _policy.ComputeDelay(attempt, _jitterSource());
                if (delay > TimeSpan.Zero)
                    await Task.Delay(delay, _timeProvider, ct).ConfigureAwait(false);
            }
        }

        // lastException is guaranteed non-null here: we only break out of the loop after
        // catching at least one exception on the final attempt.
        throw lastException!;
    }
}
