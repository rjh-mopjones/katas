namespace Katas.Tests.Disposables;

using Katas.Disposables;

public sealed class FileHandleSimTests
{
    [Fact]
    public void Dispose_Should_BeIdempotent_WhenCalledMultipleTimes()
    {
        FileHandleSim handle = new("test.txt");
        handle.Dispose();
        handle.Dispose(); // must not throw
    }

    [Fact]
    public void UsingBlock_Should_DisposeHandle()
    {
        FileHandleSim handle;
        using (handle = new FileHandleSim("test.txt"))
        {
            Assert.True(handle.IsOpen);
        }

        Assert.False(handle.IsOpen);
    }

    [Fact]
    public void Read_Should_ThrowObjectDisposedException_AfterDispose()
    {
        FileHandleSim handle = new("test.txt");
        handle.Dispose();

        Assert.Throws<ObjectDisposedException>(() => handle.Read());
    }

    [Fact]
    public void Write_Should_ThrowObjectDisposedException_AfterDispose()
    {
        FileHandleSim handle = new("test.txt");
        handle.Dispose();

        Assert.Throws<ObjectDisposedException>(() => handle.Write("data"));
    }

    [Fact]
    public void Read_Should_Succeed_WhenNotDisposed()
    {
        using FileHandleSim handle = new("test.txt");
        string result = handle.Read();
        Assert.Contains("test.txt", result);
    }
}
