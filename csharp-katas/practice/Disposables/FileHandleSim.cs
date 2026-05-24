namespace Katas.Disposables;

/// <summary>
/// Simulates a file handle to illustrate the canonical synchronous dispose pattern.
/// </summary>
public class FileHandleSim : IDisposable
{
    /// <summary>Name of the simulated file, for display/test purposes.</summary>
    public string FileName => throw new NotImplementedException();

    /// <summary>Whether the simulated handle is currently open.</summary>
    public bool IsOpen => throw new NotImplementedException();

    /// <summary>Opens the simulated file handle.</summary>
    public FileHandleSim(string fileName) { throw new NotImplementedException(); }

    /// <summary>Reads from the simulated file.</summary>
    public string Read() => throw new NotImplementedException();

    /// <summary>Writes to the simulated file.</summary>
    public void Write(string data) => throw new NotImplementedException();

    /// <inheritdoc/>
    public void Dispose() { throw new NotImplementedException(); }

    /// <summary>Core disposal logic; subclasses can override to extend.</summary>
    protected virtual void Dispose(bool disposing) { throw new NotImplementedException(); }

    /// <summary>Throws <see cref="ObjectDisposedException"/> if this instance has been disposed.</summary>
    protected void ThrowIfDisposed() { throw new NotImplementedException(); }
}
