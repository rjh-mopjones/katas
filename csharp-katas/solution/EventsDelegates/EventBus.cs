using System.Collections.Concurrent;

namespace Katas.EventsDelegates;

/// <summary>
/// A lightweight, thread-safe, type-keyed publish/subscribe bus backed by delegates.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why delegates instead of events?</b>  .NET events are syntactic sugar over a delegate field
/// plus <c>add</c>/<c>remove</c> accessors.  An event restricts external callers to <c>+=</c> and
/// <c>-=</c> only; they cannot invoke or replace the delegate.  Here we want callers to
/// <em>subscribe</em> (add) via <see cref="Subscribe{T}"/> and <em>unsubscribe</em> by disposing
/// the returned token — a richer pattern than raw <c>+=/-=</c>.  Storing bare delegates in the
/// dictionary is therefore cleaner.
/// </para>
/// <para>
/// <b>Thread safety:</b> <see cref="ConcurrentDictionary{TKey,TValue}"/> provides safe concurrent
/// access to the per-type handler lists.  The inner list is protected by a lock so that
/// subscribe/unsubscribe and publish are mutually exclusive per type.
/// </para>
/// <para>
/// <b>Isolating faulty handlers:</b> Each handler is invoked inside a <c>try/catch</c> during
/// <see cref="Publish{T}"/>.  The choice to swallow (not rethrow) is deliberate: a bus is a
/// fan-out mechanism; one subscriber's bug must not silently deprive all other subscribers of the
/// event.  Production code would typically log the exception here.
/// Trade-off: if you <em>do</em> want failures to propagate use <c>AggregateException</c> to
/// collect them and throw after all handlers have run.
/// </para>
/// </remarks>
public sealed class EventBus
{
    // Maps an event type to the list of handlers registered for that type.
    private readonly ConcurrentDictionary<Type, HandlerList> _handlers = new();

    /// <summary>
    /// Subscribes <paramref name="handler"/> to all published events of type <typeparamref name="T"/>.
    /// </summary>
    /// <typeparam name="T">The event payload type.</typeparam>
    /// <param name="handler">Delegate to invoke when an event of type <typeparamref name="T"/> is published.</param>
    /// <returns>
    /// A disposable subscription token.  Disposing it removes the handler from the bus.
    /// The caller should hold a reference (e.g. in a <c>using</c> block or field) for as long as
    /// the subscription should remain active.
    /// </returns>
    public IDisposable Subscribe<T>(Action<T> handler)
    {
        HandlerList list = _handlers.GetOrAdd(typeof(T), _ => new HandlerList());
        list.Add(handler);
        return new Subscription(() => list.Remove(handler));
    }

    /// <summary>
    /// Publishes <paramref name="event"/> to all handlers registered for <typeparamref name="T"/>.
    /// </summary>
    /// <typeparam name="T">The event payload type.</typeparam>
    /// <param name="event">The event to deliver.</param>
    /// <remarks>
    /// If no handlers are registered for <typeparamref name="T"/> this is a no-op.
    /// Handlers are invoked synchronously on the calling thread, one by one.
    /// A handler that throws does not prevent subsequent handlers from receiving the event.
    /// </remarks>
    public void Publish<T>(T @event)
    {
        if (!_handlers.TryGetValue(typeof(T), out HandlerList? list))
            return;

        foreach (Delegate handler in list.Snapshot())
        {
            try
            {
                ((Action<T>)handler)(@event);
            }
            catch
            {
                // Intentionally swallowed: isolate faulty handlers so all others still receive the event.
                // In production, log the exception here before continuing.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /// <summary>Thread-safe list of delegates for a single event type.</summary>
    private sealed class HandlerList
    {
        private readonly object _lock = new();
        private readonly List<Delegate> _delegates = new();

        public void Add(Delegate d)
        {
            lock (_lock) _delegates.Add(d);
        }

        public void Remove(Delegate d)
        {
            lock (_lock) _delegates.Remove(d);
        }

        /// <summary>Returns a snapshot copy so callers can iterate without holding the lock.</summary>
        public Delegate[] Snapshot()
        {
            lock (_lock) return _delegates.ToArray();
        }
    }

    /// <summary>Disposes by invoking a teardown callback exactly once.</summary>
    private sealed class Subscription : IDisposable
    {
        private Action? _teardown;

        public Subscription(Action teardown) => _teardown = teardown;

        public void Dispose()
        {
            // Atomically swap out the teardown so it only runs once even if Dispose is called
            // concurrently from multiple threads.
            Action? teardown = Interlocked.Exchange(ref _teardown, null);
            teardown?.Invoke();
        }
    }
}
