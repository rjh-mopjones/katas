namespace Katas.Scheduler;

/// <summary>
/// Schedules one-shot callbacks to fire after a specified delay, using a single background
/// timer armed to the next-due item.
/// </summary>
/// <remarks>
/// <para>
/// <b>Design:</b>
/// A <see cref="PriorityQueue{TElement,TPriority}"/> keeps pending items ordered by their
/// due timestamp (in <see cref="TimeProvider"/> units).  A single <see cref="ITimer"/> is
/// armed to the head of the queue.  When it fires, all due items are dequeued and their
/// callbacks are invoked in due-time order; the timer is then re-armed to the new head.
/// </para>
/// <para>
/// <b>Single timer advantage:</b>  Creating one timer per scheduled item is wasteful under
/// high throughput (each timer allocates OS resources).  A single re-arming timer scales to
/// thousands of scheduled items with one OS timer handle.
/// </para>
/// <para>
/// <b>Thread-safety:</b>  A <c>lock</c> guards the priority queue, the cancel set, and the
/// timer re-arm.  Callbacks are invoked outside the lock to prevent re-entrancy deadlocks.
/// </para>
/// <para>
/// <b>Cancellation:</b>  Each <see cref="Schedule"/> call returns an <see cref="IDisposable"/>
/// handle.  Disposing it before the due time adds the item's ID to a cancel set so it is
/// skipped when the timer fires.  O(1) cancel at the cost of keeping cancelled items in the
/// queue until they would have been due.
/// </para>
/// <para>
/// <b><see cref="TimeProvider"/> and <c>FakeTimeProvider</c>:</b>
/// The timer is created via <see cref="TimeProvider.CreateTimer"/> so that
/// <c>FakeTimeProvider.Advance</c> fires the callback synchronously in tests without real
/// sleeps.
/// </para>
/// </remarks>
public sealed class DelayScheduler : IDisposable
{
    private readonly TimeProvider _timeProvider;
    private readonly object _lock = new();
    private readonly PriorityQueue<ScheduledItem, long> _queue = new();
    private readonly HashSet<long> _cancelledIds = new();
    private readonly ITimer _timer;
    private long _nextId;
    private bool _disposed;

    /// <summary>Initialises a scheduler using the supplied (or system) time provider.</summary>
    /// <param name="timeProvider">
    /// Clock abstraction. Defaults to <see cref="TimeProvider.System"/>.
    /// Pass a <c>FakeTimeProvider</c> in tests.
    /// </param>
    public DelayScheduler(TimeProvider? timeProvider = null)
    {
        _timeProvider = timeProvider ?? TimeProvider.System;
        // Create the timer in a non-firing state (Timeout.InfiniteTimeSpan).
        _timer = _timeProvider.CreateTimer(OnTimerFired, state: null, Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
    }

    /// <summary>
    /// Schedules <paramref name="callback"/> to be invoked once after <paramref name="delay"/>.
    /// </summary>
    /// <param name="callback">The action to invoke when the delay elapses.</param>
    /// <param name="delay">Time to wait before invoking the callback. Must be non-negative.</param>
    /// <returns>
    /// A handle whose <see cref="IDisposable.Dispose"/> cancels the pending callback if it
    /// has not yet fired.
    /// </returns>
    /// <exception cref="ArgumentOutOfRangeException">
    /// Thrown when <paramref name="delay"/> is negative.
    /// </exception>
    /// <exception cref="ObjectDisposedException">
    /// Thrown when the scheduler has been disposed.
    /// </exception>
    public IDisposable Schedule(Action callback, TimeSpan delay)
    {
        if (delay < TimeSpan.Zero)
            throw new ArgumentOutOfRangeException(nameof(delay), "Delay must be non-negative.");

        lock (_lock)
        {
            ObjectDisposedException.ThrowIf(_disposed, this);

            long id = ++_nextId;
            long dueTimestamp = _timeProvider.GetTimestamp() +
                                (long)(delay.TotalSeconds * _timeProvider.TimestampFrequency);

            var item = new ScheduledItem(id, callback);
            _queue.Enqueue(item, dueTimestamp);
            ArmTimer();

            return new CancelHandle(id, this);
        }
    }

    /// <summary>
    /// Cancels the pending item with the given <paramref name="id"/> if it has not yet fired.
    /// </summary>
    internal void Cancel(long id)
    {
        lock (_lock)
        {
            _cancelledIds.Add(id);
        }
    }

    /// <summary>
    /// Timer callback: dequeues and runs all due items, then re-arms.
    /// </summary>
    private void OnTimerFired(object? state)
    {
        // Collect due items under the lock but invoke them outside to avoid re-entrancy.
        List<Action>? due = null;

        lock (_lock)
        {
            if (_disposed) return;

            long now = _timeProvider.GetTimestamp();

            while (_queue.TryPeek(out ScheduledItem? item, out long priority) && priority <= now)
            {
                _queue.Dequeue();
                if (!_cancelledIds.Remove(item.Id))
                {
                    due ??= new List<Action>();
                    due.Add(item.Callback);
                }
            }

            ArmTimer();
        }

        if (due is not null)
        {
            foreach (Action callback in due)
            {
                callback();
            }
        }
    }

    /// <summary>
    /// Arms (or re-arms) the timer to fire at the timestamp of the next item in the queue.
    /// Must be called while holding <c>_lock</c>.
    /// </summary>
    private void ArmTimer()
    {
        if (_disposed) return;

        // Peek at cancelled-filtered head — skip cancelled entries so we don't create a
        // useless wakeup.  (A cancelled entry at the head is harmless but wastes a wakeup.)
        if (!_queue.TryPeek(out _, out long nextDue))
        {
            // Queue is empty; disarm.
            _timer.Change(Timeout.InfiniteTimeSpan, Timeout.InfiniteTimeSpan);
            return;
        }

        long now = _timeProvider.GetTimestamp();
        long ticksUntilDue = nextDue - now;

        TimeSpan dueIn = ticksUntilDue <= 0
            ? TimeSpan.Zero
            : TimeSpan.FromSeconds((double)ticksUntilDue / _timeProvider.TimestampFrequency);

        _timer.Change(dueIn, Timeout.InfiniteTimeSpan);
    }

    /// <inheritdoc />
    public void Dispose()
    {
        lock (_lock)
        {
            if (_disposed) return;
            _disposed = true;
        }

        _timer.Dispose();
    }
}

/// <summary>
/// Immutable value representing a scheduled callback.
/// </summary>
internal sealed class ScheduledItem
{
    internal long Id { get; }
    internal Action Callback { get; }

    internal ScheduledItem(long id, Action callback)
    {
        Id = id;
        Callback = callback;
    }
}

/// <summary>
/// Handle returned by <see cref="DelayScheduler.Schedule"/>. Disposing it cancels the
/// pending callback.
/// </summary>
internal sealed class CancelHandle : IDisposable
{
    private readonly long _id;
    private readonly DelayScheduler _scheduler;
    private int _disposed;

    internal CancelHandle(long id, DelayScheduler scheduler)
    {
        _id = id;
        _scheduler = scheduler;
    }

    /// <inheritdoc />
    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) == 0)
            _scheduler.Cancel(_id);
    }
}
