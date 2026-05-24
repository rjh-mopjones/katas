namespace Katas.Generics;

/// <summary>
/// A discriminated union representing either a successful value (<c>Ok</c>) or an error (<c>Err</c>).
/// </summary>
/// <typeparam name="T">Type of the success value.</typeparam>
/// <typeparam name="TError">Type of the error value.</typeparam>
/// <remarks>
/// <para>
/// <b>Why two type parameters?</b>  Keeping the error type generic avoids boxing (no casting to
/// <c>Exception</c>) and allows domain-specific error types such as enums, problem-detail records,
/// or validation lists.  Compare with Haskell's <c>Either</c> or Rust's <c>Result</c>.
/// </para>
/// <para>
/// <b>Railway-oriented programming:</b> <c>Map</c> and <c>Bind</c> thread the happy path forward;
/// any <c>Err</c> short-circuits the chain without explicit branching.  This is the "two-track"
/// approach described by Scott Wlaschin.
/// </para>
/// <para>
/// <b>Struct vs class:</b> Same trade-offs as <c>Option&lt;T&gt;</c>.  A readonly struct is
/// zero-allocation but copies on every assignment; for large <typeparamref name="T"/> or
/// <typeparamref name="TError"/> a class hierarchy might be preferable.
/// </para>
/// </remarks>
public readonly struct Result<T, TError>
{
    private readonly T _value;
    private readonly TError _error;

    /// <summary><c>true</c> when the result represents a success.</summary>
    public bool IsOk { get; }

    private Result(T value)
    {
        _value = value;
        _error = default!;
        IsOk = true;
    }

    private Result(TError error)
    {
        _value = default!;
        _error = error;
        IsOk = false;
    }

    /// <summary>Creates a success result wrapping <paramref name="value"/>.</summary>
    public static Result<T, TError> Ok(T value) => new(value);

    /// <summary>Creates an error result wrapping <paramref name="error"/>.</summary>
    public static Result<T, TError> Err(TError error) => new(error);

    /// <summary>
    /// Transforms the success value with <paramref name="selector"/>; propagates <c>Err</c> unchanged.
    /// </summary>
    /// <typeparam name="TResult">Type of the mapped success value.</typeparam>
    /// <param name="selector">Pure projection applied on the happy path.</param>
    /// <remarks>
    /// If <paramref name="selector"/> can itself fail, use <see cref="Bind{TResult}"/> instead so
    /// that the error is represented in the return type rather than thrown as an exception.
    /// </remarks>
    public Result<TResult, TError> Map<TResult>(Func<T, TResult> selector)
    {
        if (!IsOk) return Result<TResult, TError>.Err(_error);
        return Result<TResult, TError>.Ok(selector(_value));
    }

    /// <summary>
    /// Chains a fallible operation; if either step is <c>Err</c> the error propagates.
    /// </summary>
    /// <typeparam name="TResult">Success type returned by <paramref name="binder"/>.</typeparam>
    /// <param name="binder">A function from <typeparamref name="T"/> to a new <c>Result</c>.</param>
    public Result<TResult, TError> Bind<TResult>(Func<T, Result<TResult, TError>> binder)
    {
        if (!IsOk) return Result<TResult, TError>.Err(_error);
        return binder(_value);
    }

    /// <summary>
    /// Eliminates the result by applying one of two continuations.
    /// </summary>
    /// <typeparam name="TResult">Shared return type of both continuations.</typeparam>
    /// <param name="ok">Called with the success value.</param>
    /// <param name="err">Called with the error value.</param>
    public TResult Match<TResult>(Func<T, TResult> ok, Func<TError, TResult> err)
    {
        return IsOk ? ok(_value) : err(_error);
    }

    /// <inheritdoc/>
    public override string ToString() =>
        IsOk ? $"Ok({_value})" : $"Err({_error})";
}
