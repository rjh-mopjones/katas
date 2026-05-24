namespace Katas.Disposables;

/// <summary>
/// Holds a collection of <see cref="IDisposable"/> children and disposes them all
/// (in reverse-add order) when this instance itself is disposed.
/// </summary>
public sealed class CompositeDisposable : IDisposable
{
    /// <summary>Adds <paramref name="disposable"/> to the group.</summary>
    public void Add(IDisposable disposable) => throw new NotImplementedException();

    /// <summary>Disposes all children in reverse-add order, then prevents further additions.</summary>
    public void Dispose() { throw new NotImplementedException(); }
}
