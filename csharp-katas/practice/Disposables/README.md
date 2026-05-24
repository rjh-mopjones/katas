# Disposables

Implement `IDisposable`, `IAsyncDisposable`, and a composite that disposes children in reverse order.

## The problem

Correctly releasing resources is one of the most error-prone parts of .NET programming. Implement three types that each demonstrate a different facet of the dispose pattern.

## Requirements

**FileHandleSim** (`IDisposable`)
- Constructor accepts a `fileName`; `FileName` and `IsOpen` properties reflect state.
- `Read()` and `Write()` throw `ObjectDisposedException` after disposal.
- `Dispose()` must call `Dispose(true)` + `GC.SuppressFinalize(this)`.
- `Dispose(bool disposing)` must be idempotent (second call is a no-op).

**AsyncConnection** (`IAsyncDisposable`)
- `QueryAsync` throws `ObjectDisposedException` after disposal.
- `DisposeAsync()` returns `ValueTask` (not `Task`), is idempotent, and is NOT declared `async` in the skeleton.
- Use `Interlocked.Exchange` to make concurrent disposal safe.

**CompositeDisposable** (`IDisposable`)
- `Add(IDisposable)` throws `ObjectDisposedException` if already disposed.
- `Dispose()` releases all children in reverse-add order (LIFO), then clears the list.
- Calling `Dispose()` a second time must be a safe no-op.

## What you implement

```csharp
public class FileHandleSim : IDisposable
{
    public string FileName { get; }
    public bool IsOpen { get; }
    public FileHandleSim(string fileName);
    public string Read();
    public void Write(string data);
    public void Dispose();
    protected virtual void Dispose(bool disposing);
    protected void ThrowIfDisposed();
}

public sealed class AsyncConnection : IAsyncDisposable
{
    public bool IsConnected { get; }
    public Task<string> QueryAsync(string query, CancellationToken ct = default);
    public ValueTask DisposeAsync();
}

public sealed class CompositeDisposable : IDisposable
{
    public void Add(IDisposable disposable);
    public void Dispose();
}
```

## The real challenge

- `DisposeAsync()` returns `ValueTask` — declare it with a block body returning `ValueTask.CompletedTask` for the already-disposed path, and wrapping real async work with `new ValueTask(Task.Run(...))` or similar. Do NOT use the `async` keyword in the skeleton.
- Reverse-order disposal in `CompositeDisposable` mirrors the natural stack-unwind of nested `using` statements.

## Run

Write your own tests under `practice.tests/Disposables/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Disposables"
```

## Reference

`solution/Disposables/` — see `FileHandleSim.cs`, `AsyncConnection.cs`, and `CompositeDisposable.cs`.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/standard/garbage-collection/implementing-dispose
