namespace Katas.Disposables;

/// <summary>
/// Simulates an async-disposable connection resource (e.g. a database or HTTP client).
/// </summary>
/// <remarks>
/// <para>
/// <b>IAsyncDisposable:</b> Introduced in .NET Core 3.0 for resources whose teardown requires
/// async operations (e.g. flushing a network buffer with <c>await stream.FlushAsync()</c>).
/// The <c>await using</c> statement calls <see cref="DisposeAsync"/> automatically.
/// </para>
/// <para>
/// <b>ValueTask vs Task for DisposeAsync:</b> The BCL uses <c>ValueTask</c> because disposal is
/// often a no-op on the second call (already disposed) — <c>ValueTask</c> avoids a heap allocation
/// in the common synchronous-completion case.  Use <c>Task</c> if the implementation always
/// allocates anyway (e.g. wraps a <c>Task</c>-returning API).
/// </para>
/// <para>
/// <b>Idempotency:</b> An idempotent <c>DisposeAsync</c> uses a flag (<c>_disposed</c>) checked
/// with <see cref="Interlocked.Exchange"/> to safely handle concurrent calls.  The second call
/// returns a completed <c>ValueTask</c> without doing any work.
/// </para>
/// </remarks>
public sealed class AsyncConnection : IAsyncDisposable
{
    private int _disposed; // 0 = open, 1 = disposed (use int for Interlocked)

    /// <summary>Whether the connection is still open.</summary>
    public bool IsConnected => _disposed == 0;

    /// <summary>
    /// Sends a query over the simulated connection.
    /// </summary>
    /// <param name="query">The query to execute.</param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>A placeholder result string.</returns>
    /// <exception cref="ObjectDisposedException">Thrown if the connection has been disposed.</exception>
    public async Task<string> QueryAsync(string query, CancellationToken ct = default)
    {
        ThrowIfDisposed();
        await Task.Yield(); // simulate async I/O
        return $"result of: {query}";
    }

    /// <inheritdoc/>
    /// <remarks>
    /// Idempotent: safe to call multiple times or concurrently.  The first caller wins the
    /// <c>Interlocked.Exchange</c> and performs cleanup; subsequent callers find <c>_disposed == 1</c>
    /// and return immediately.
    /// </remarks>
    public async ValueTask DisposeAsync()
    {
        if (Interlocked.Exchange(ref _disposed, 1) != 0)
            return; // already disposed — idempotent

        // Simulate async teardown (e.g. closing the TCP connection gracefully).
        await Task.Yield();
    }

    private void ThrowIfDisposed()
    {
        if (_disposed != 0)
            throw new ObjectDisposedException(nameof(AsyncConnection));
    }
}
