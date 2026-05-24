namespace Katas.Generics;

/// <summary>
/// A discriminated union representing either a successful value (<c>Ok</c>) or an error (<c>Err</c>).
/// </summary>
public readonly struct Result<T, TError>
{
    /// <summary><c>true</c> when the result represents a success.</summary>
    public bool IsOk => throw new NotImplementedException();

    /// <summary>Creates a success result wrapping <paramref name="value"/>.</summary>
    public static Result<T, TError> Ok(T value) => throw new NotImplementedException();

    /// <summary>Creates an error result wrapping <paramref name="error"/>.</summary>
    public static Result<T, TError> Err(TError error) => throw new NotImplementedException();

    /// <summary>Transforms the success value with <paramref name="selector"/>; propagates <c>Err</c> unchanged.</summary>
    public Result<TResult, TError> Map<TResult>(Func<T, TResult> selector) => throw new NotImplementedException();

    /// <summary>Chains a fallible operation; if either step is <c>Err</c> the error propagates.</summary>
    public Result<TResult, TError> Bind<TResult>(Func<T, Result<TResult, TError>> binder) => throw new NotImplementedException();

    /// <summary>Eliminates the result by applying one of two continuations.</summary>
    public TResult Match<TResult>(Func<T, TResult> ok, Func<TError, TResult> err) => throw new NotImplementedException();

    /// <inheritdoc/>
    public override string ToString() => throw new NotImplementedException();
}
