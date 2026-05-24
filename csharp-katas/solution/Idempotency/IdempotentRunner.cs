using System.Collections.Concurrent;

namespace Katas.Idempotency;

/// <summary>
/// Guarantees that an async action runs at most once per string key, even under concurrent
/// duplicate calls, returning the same cached result (or exception) for all callers.
/// </summary>
/// <remarks>
/// <para>
/// <b>Concurrency problem without this class (TOCTOU race):</b>
/// A naive approach — "check if key exists, if not run and store" — has a
/// time-of-check/time-of-use race.  Between the check and the store, N threads may each see
/// the key absent and each independently start the action, running it N times.
/// </para>
/// <para>
/// <b>Solution — <c>ConcurrentDictionary.GetOrAdd</c> + <c>Lazy</c>:</b>
/// <see cref="ConcurrentDictionary{TKey,TValue}.GetOrAdd(TKey,Func{TKey,TValue})"/> guarantees
/// that exactly one value is associated with a key after the call, but the factory <em>may</em>
/// be invoked by multiple threads concurrently (the extra results are discarded).  Wrapping the
/// <c>Task</c> in a <see cref="Lazy{T}"/> means the factory is called at most once per logical
/// slot: <c>Lazy</c> uses its own lock so only one thread actually calls <c>action</c>.  The
/// winner's task is stored; all other callers <c>await</c> the same task instance.
/// </para>
/// <para>
/// <b>Implementation choice — <c>object</c>-typed dictionary:</b>
/// Because the return type <c>T</c> varies per call site, a single typed dictionary is not
/// possible.  We store <c>Lazy&lt;Task&lt;T&gt;&gt;</c> boxed as <c>object</c> and cast on
/// retrieval.  Callers are responsible for consistently using the same <c>T</c> for a given
/// key; mixing types on the same key will throw <see cref="InvalidCastException"/>.
/// </para>
/// <para>
/// <b>Exception caching:</b>  Because the task is cached before it completes, if the action
/// throws, the faulted <see cref="Task{T}"/> is stored and all subsequent awaits of that key
/// will re-observe the same exception.  This is intentional: it prevents retry storms, but
/// callers that need retry-on-error should use a different key or clear the entry.
/// </para>
/// <para>
/// <b>Null results:</b>  A <c>null</c> return value from the action is cached and returned
/// normally; callers handle nullability on <c>T</c>.
/// </para>
/// </remarks>
public sealed class IdempotentRunner
{
    // Stores Lazy<Task<T>> boxed as object, keyed by the idempotency key.
    private readonly ConcurrentDictionary<string, object> _cache = new();

    /// <summary>
    /// Runs <paramref name="action"/> for the given <paramref name="key"/> exactly once,
    /// returning its result to all concurrent or subsequent callers with the same key.
    /// </summary>
    /// <typeparam name="T">The result type of the action.</typeparam>
    /// <param name="key">
    /// A string that identifies this logical operation. Duplicate calls with the same key
    /// receive the cached result.
    /// </param>
    /// <param name="action">
    /// The idempotent operation to execute. This delegate will be invoked at most once per
    /// unique key per <see cref="IdempotentRunner"/> instance.
    /// </param>
    /// <param name="ct">Cancellation token forwarded to the action on first execution only.</param>
    /// <returns>
    /// A task that resolves to the result produced by the first execution of
    /// <paramref name="action"/> for <paramref name="key"/>.
    /// </returns>
    /// <exception cref="ArgumentException">
    /// Thrown when <paramref name="key"/> is null or empty.
    /// </exception>
    public Task<T> RunOnceAsync<T>(
        string key,
        Func<CancellationToken, Task<T>> action,
        CancellationToken ct = default)
    {
        if (string.IsNullOrEmpty(key))
            throw new ArgumentException("Key must not be null or empty.", nameof(key));

        // GetOrAdd is thread-safe: even if multiple threads call it simultaneously for the
        // same key, only one Lazy<Task<T>> will win the race and be stored.  The Lazy wrapper
        // ensures the inner factory (which starts the Task) executes at most once.
        var lazy = (Lazy<Task<T>>)_cache.GetOrAdd(
            key,
            _ => new Lazy<Task<T>>(() => action(ct)));

        // .Value starts the Lazy if not already started. Multiple threads hitting this
        // concurrently are all serialised inside Lazy's own lock; the second+ just read the
        // already-started task.
        return lazy.Value;
    }
}
