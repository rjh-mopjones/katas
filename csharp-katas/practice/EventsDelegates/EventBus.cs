namespace Katas.EventsDelegates;

/// <summary>
/// A lightweight, thread-safe, type-keyed publish/subscribe bus backed by delegates.
/// </summary>
public sealed class EventBus
{
    /// <summary>
    /// Subscribes <paramref name="handler"/> to all published events of type <typeparamref name="T"/>.
    /// Returns a disposable token; disposing it removes the handler from the bus.
    /// </summary>
    public IDisposable Subscribe<T>(Action<T> handler) => throw new NotImplementedException();

    /// <summary>
    /// Publishes <paramref name="event"/> to all handlers registered for <typeparamref name="T"/>.
    /// </summary>
    public void Publish<T>(T @event) => throw new NotImplementedException();
}
