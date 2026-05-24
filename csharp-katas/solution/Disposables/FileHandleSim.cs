namespace Katas.Disposables;

/// <summary>
/// Simulates a file handle to illustrate the canonical synchronous dispose pattern.
/// </summary>
/// <remarks>
/// <para>
/// <b>The dispose pattern (IDisposable):</b>
/// <list type="number">
///   <item><description>
///     Public <c>Dispose()</c> calls <c>Dispose(true)</c> then <c>GC.SuppressFinalize(this)</c>.
///     Suppressing finalization is important: we've already cleaned up unmanaged resources so
///     the GC finaliser thread doesn't need to do it again.
///   </description></item>
///   <item><description>
///     <c>protected virtual void Dispose(bool disposing)</c> is the extension point for subclasses.
///     When <paramref name="disposing"/> is <c>true</c> the call came from <c>IDisposable.Dispose</c>
///     and it is safe to touch managed objects.  When <c>false</c> the call came from the finaliser;
///     managed objects may already be collected, so touch only unmanaged resources.
///   </description></item>
///   <item><description>
///     The <c>_disposed</c> flag makes <c>Dispose</c> idempotent: calling it multiple times is safe
///     and the second call is a no-op.
///   </description></item>
/// </list>
/// </para>
/// <para>
/// <b>When does the finaliser apply?</b>  Only when the class wraps a raw unmanaged handle
/// (e.g. a Win32 <c>HANDLE</c>).  If all held resources are themselves <c>IDisposable</c>
/// (managed wrappers), skip the finaliser and simply implement <c>IDisposable</c> with the
/// two-argument overload.
/// </para>
/// </remarks>
public class FileHandleSim : IDisposable
{
    private bool _disposed;

    /// <summary>Name of the simulated file, for display/test purposes.</summary>
    public string FileName { get; }

    /// <summary>Whether the simulated handle is currently open.</summary>
    public bool IsOpen => !_disposed;

    /// <summary>Opens the simulated file handle.</summary>
    /// <param name="fileName">Path of the file to open.</param>
    public FileHandleSim(string fileName)
    {
        FileName = fileName;
    }

    /// <summary>
    /// Reads from the simulated file.
    /// </summary>
    /// <returns>A placeholder string representing read data.</returns>
    /// <exception cref="ObjectDisposedException">Thrown if the handle has been disposed.</exception>
    public string Read()
    {
        ThrowIfDisposed();
        return $"data from {FileName}";
    }

    /// <summary>
    /// Writes to the simulated file.
    /// </summary>
    /// <param name="data">Data to write.</param>
    /// <exception cref="ObjectDisposedException">Thrown if the handle has been disposed.</exception>
    public void Write(string data)
    {
        ThrowIfDisposed();
        // In a real implementation, stream.Write(data) would go here.
        _ = data; // suppress unused-variable warning
    }

    /// <inheritdoc/>
    /// <remarks>
    /// Calls <see cref="Dispose(bool)"/> with <c>disposing = true</c> and suppresses the finaliser
    /// (if any) so the GC does not double-free unmanaged resources.
    /// </remarks>
    public void Dispose()
    {
        Dispose(disposing: true);
        GC.SuppressFinalize(this);
    }

    /// <summary>
    /// Core disposal logic, separated so subclasses can extend and so the finaliser path is clean.
    /// </summary>
    /// <param name="disposing">
    /// <c>true</c> when called from <see cref="Dispose()"/>; <c>false</c> when called from a finaliser.
    /// Only access managed resources when <paramref name="disposing"/> is <c>true</c>.
    /// </param>
    protected virtual void Dispose(bool disposing)
    {
        if (_disposed) return;

        if (disposing)
        {
            // Release managed resources here (e.g. close a StreamReader).
        }

        // Release unmanaged resources here (e.g. CloseHandle(nativeHandle)).

        _disposed = true;
    }

    /// <summary>
    /// Throws <see cref="ObjectDisposedException"/> if this instance has been disposed.
    /// </summary>
    /// <exception cref="ObjectDisposedException">Always thrown when <c>_disposed</c> is <c>true</c>.</exception>
    protected void ThrowIfDisposed()
    {
        if (_disposed)
            throw new ObjectDisposedException(GetType().Name);
    }
}
