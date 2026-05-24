# Generics

Generic constraints, variance annotations, and functional container types.

## The problem

Implement `Option<T>` and `Result<T, TError>` as `readonly struct` discriminated unions. Implement `StringProducer` and `ObjectConsumer` to explore covariance and contravariance on the provided `IProducer<out T>` and `IConsumer<in T>` interfaces.

## Requirements

**Option\<T\>**
- `Some(value)` factory and `None` sentinel; `IsSome` property.
- `Map`, `Bind`, `Match`, `GetValueOr` follow standard functor/monad laws.
- `ToString()` returns `"Some(value)"` or `"None"`.

**Result\<T, TError\>**
- `Ok(value)` and `Err(error)` factories; `IsOk` property.
- `Map`, `Bind`, `Match` thread the happy path; errors short-circuit.
- `ToString()` returns `"Ok(value)"` or `"Err(error)"`.

**Variance**
- `StringProducer` implements `IProducer<string>` with a constant value.
- `ObjectConsumer` implements `IConsumer<object>`, recording items as strings; exposes `Received`.

## What you implement

```csharp
public readonly struct Option<T>
{
    public bool IsSome { get; }
    public static Option<T> Some(T value);
    public static readonly Option<T> None;
    public Option<TResult> Map<TResult>(Func<T, TResult> selector);
    public Option<TResult> Bind<TResult>(Func<T, Option<TResult>> binder);
    public TResult Match<TResult>(Func<T, TResult> some, Func<TResult> none);
    public T GetValueOr(T fallback);
    public override string ToString();
}

public readonly struct Result<T, TError>
{
    public bool IsOk { get; }
    public static Result<T, TError> Ok(T value);
    public static Result<T, TError> Err(TError error);
    public Result<TResult, TError> Map<TResult>(Func<T, TResult> selector);
    public Result<TResult, TError> Bind<TResult>(Func<T, Result<TResult, TError>> binder);
    public TResult Match<TResult>(Func<T, TResult> ok, Func<TError, TResult> err);
    public override string ToString();
}

public sealed class StringProducer : IProducer<string>
{
    public StringProducer(string value);
    public string Produce();
}

public sealed class ObjectConsumer : IConsumer<object>
{
    public IReadOnlyList<string> Received { get; }
    public void Consume(object item);
}
```

`IProducer<out T>` and `IConsumer<in T>` are provided — do not modify them.

## The real challenge

- A `readonly struct` cannot store private fields in the skeleton — but the real implementation needs them. Think about how `IsSome` must be stored alongside the value.
- `None` is just `default(Option<T>)` — no allocation needed.
- Variance: verify that `StringProducer` can be assigned to `IProducer<object>`, and `ObjectConsumer` to `IConsumer<string>`.

## Run

Write your own tests under `practice.tests/Generics/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Generics"
```

## Reference

`solution/Generics/` — see `Option.cs`, `Result.cs`, and `Variance.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/programming-guide/concepts/covariance-contravariance/
