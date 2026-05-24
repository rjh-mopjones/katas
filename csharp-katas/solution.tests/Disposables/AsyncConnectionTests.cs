namespace Katas.Tests.Disposables;

using Katas.Disposables;

public sealed class AsyncConnectionTests
{
    [Fact]
    public async Task DisposeAsync_Should_BeIdempotent_WhenCalledMultipleTimes()
    {
        AsyncConnection conn = new();
        await conn.DisposeAsync();
        await conn.DisposeAsync(); // must not throw
    }

    [Fact]
    public async Task AwaitUsing_Should_DisposeConnection()
    {
        AsyncConnection conn;
        conn = new AsyncConnection();

        await using (conn)
        {
            Assert.True(conn.IsConnected);
        }

        Assert.False(conn.IsConnected);
    }

    [Fact]
    public async Task QueryAsync_Should_ThrowObjectDisposedException_AfterDispose()
    {
        AsyncConnection conn = new();
        await conn.DisposeAsync();

        await Assert.ThrowsAsync<ObjectDisposedException>(() => conn.QueryAsync("SELECT 1"));
    }

    [Fact]
    public async Task QueryAsync_Should_ReturnResult_WhenConnected()
    {
        await using AsyncConnection conn = new();
        string result = await conn.QueryAsync("SELECT 1");
        Assert.Contains("SELECT 1", result);
    }
}
