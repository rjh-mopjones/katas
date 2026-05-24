namespace Katas.Disposables;

/// <summary>
/// Simulates an async-disposable connection resource (e.g. a database or HTTP client).
/// </summary>
public sealed class AsyncConnection : IAsyncDisposable
{
    /// <summary>Whether the connection is still open.</summary>
    public bool IsConnected => throw new NotImplementedException();

    /// <summary>Sends a query over the simulated connection.</summary>
    public Task<string> QueryAsync(string query, CancellationToken ct = default) => throw new NotImplementedException();

    /// <inheritdoc/>
    public ValueTask DisposeAsync() => throw new NotImplementedException();
}
