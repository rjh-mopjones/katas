# EventsDelegates

The .NET event pattern with `EventHandler<T>`, and a type-keyed pub/sub `EventBus` using raw delegates.

## The problem

Implement `StockTicker` following the canonical .NET event pattern: a price property that fires `PriceChanged` only when the value actually changes. Then implement a lightweight `EventBus` that lets arbitrary types publish and subscribe to events by payload type.

## Requirements

**StockTicker**
- `UpdatePrice` must only raise `PriceChanged` if the new price differs from the current price.
- `OnPriceChanged` must be `protected virtual` and use the local-copy pattern (`EventHandler<...>? handler = PriceChanged; handler?.Invoke(...)`) for thread safety.
- `CurrentPrice` reflects the most recently set price.

**EventBus**
- `Subscribe<T>` registers a handler and returns an `IDisposable` token; disposing removes the handler.
- `Publish<T>` invokes all registered handlers for that type; a faulty handler must not prevent others from receiving the event.
- Thread-safe subscribe/unsubscribe and publish.

## What you implement

```csharp
public class StockTicker
{
    public event EventHandler<PriceChangedEventArgs>? PriceChanged;
    public decimal CurrentPrice { get; }
    public void UpdatePrice(decimal newPrice);
    protected virtual void OnPriceChanged(PriceChangedEventArgs args);
}

public sealed class EventBus
{
    public IDisposable Subscribe<T>(Action<T> handler);
    public void Publish<T>(T @event);
}
```

`PriceChangedEventArgs` is provided verbatim — do not modify it.

## The real challenge

- Events cannot be expression-bodied — the `event` declaration must remain as a field-like event.
- The `EventBus` must isolate exceptions per handler (try/catch per invocation).
- The subscription token returned by `Subscribe` must be truly removable via `Dispose`, even if `Dispose` is called multiple times.

## Run

Write your own tests under `practice.tests/EventsDelegates/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~EventsDelegates"
```

## Reference

`solution/EventsDelegates/` — see `StockTicker.cs` and `EventBus.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/programming-guide/events/
