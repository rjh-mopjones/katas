namespace Katas.Disposables;

/// <summary>
/// Holds a collection of <see cref="IDisposable"/> children and disposes them all when
/// this instance itself is disposed.
/// </summary>
/// <remarks>
/// <para>
/// <b>Reverse-order disposal:</b> Children are disposed in the reverse of the order they were
/// added.  This mirrors the natural stack-unwinding order of nested <c>using</c> statements,
/// ensuring that a resource that depends on another (added first) is released before its
/// dependency.  For example: open a file, then open a writer over that file — dispose the
/// writer first, then the file.
/// </para>
/// <para>
/// <b>Each child disposed exactly once:</b>  The <c>_disposed</c> flag prevents re-entry; the
/// children list is cleared after disposal to avoid holding references.
/// </para>
/// <para>
/// <b>Exception handling:</b> If a child's <c>Dispose</c> throws, the exception propagates and
/// remaining children are not disposed.  Production implementations often catch per-child exceptions
/// and aggregate them.  The simple version here prioritises clarity.
/// </para>
/// </remarks>
public sealed class CompositeDisposable : IDisposable
{
    private readonly object _lock = new();
    private readonly List<IDisposable> _children = new();
    private bool _disposed;

    /// <summary>
    /// Adds <paramref name="disposable"/> to the group.
    /// </summary>
    /// <param name="disposable">The resource to manage.</param>
    /// <exception cref="ObjectDisposedException">
    /// Thrown if this <see cref="CompositeDisposable"/> has already been disposed.
    /// </exception>
    public void Add(IDisposable disposable)
    {
        lock (_lock)
        {
            if (_disposed)
                throw new ObjectDisposedException(nameof(CompositeDisposable));
            _children.Add(disposable);
        }
    }

    /// <summary>
    /// Disposes all children in reverse-add order, then prevents further additions.
    /// </summary>
    /// <remarks>
    /// Calling <c>Dispose</c> a second time is a safe no-op.
    /// </remarks>
    public void Dispose()
    {
        IDisposable[] toDispose;

        lock (_lock)
        {
            if (_disposed) return;
            _disposed = true;
            toDispose = _children.ToArray();
            _children.Clear();
        }

        // Dispose in reverse insertion order (stack semantics).
        for (int i = toDispose.Length - 1; i >= 0; i--)
        {
            toDispose[i].Dispose();
        }
    }
}
