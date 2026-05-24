namespace Katas.Scheduler;

/// <summary>
/// Schedules one-shot callbacks to fire after a specified delay, using a single background
/// timer armed to the next-due item.
/// </summary>
public sealed class DelayScheduler : IDisposable
{
    /// <summary>
    /// Initialises a scheduler using the supplied (or system) time provider.
    /// </summary>
    /// <param name="timeProvider">
    /// Clock abstraction. Defaults to <see cref="TimeProvider.System"/>.
    /// Pass a <c>FakeTimeProvider</c> in tests.
    /// </param>
    public DelayScheduler(TimeProvider? timeProvider = null) => throw new NotImplementedException();

    /// <summary>
    /// Schedules <paramref name="callback"/> to be invoked once after <paramref name="delay"/>.
    /// </summary>
    /// <param name="callback">The action to invoke when the delay elapses.</param>
    /// <param name="delay">Time to wait before invoking the callback. Must be non-negative.</param>
    /// <returns>
    /// A handle whose <see cref="IDisposable.Dispose"/> cancels the pending callback if it
    /// has not yet fired.
    /// </returns>
    public IDisposable Schedule(Action callback, TimeSpan delay) => throw new NotImplementedException();

    /// <inheritdoc />
    public void Dispose() => throw new NotImplementedException();
}
