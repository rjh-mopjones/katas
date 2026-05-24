namespace Katas.Generics;

/// <summary>
/// A discriminated-union value type that either holds a value (<c>Some</c>) or nothing (<c>None</c>).
/// </summary>
public readonly struct Option<T>
{
    /// <summary><c>true</c> when this instance wraps a value; <c>false</c> for <c>None</c>.</summary>
    public bool IsSome => throw new NotImplementedException();

    /// <summary>Creates a <c>Some</c> wrapping <paramref name="value"/>.</summary>
    public static Option<T> Some(T value) => throw new NotImplementedException();

    /// <summary>The singleton <c>None</c> for this <typeparamref name="T"/>.</summary>
    public static readonly Option<T> None = default;

    /// <summary>
    /// Transforms the inner value with <paramref name="selector"/> when <c>Some</c>,
    /// otherwise propagates <c>None</c>.
    /// </summary>
    public Option<TResult> Map<TResult>(Func<T, TResult> selector) => throw new NotImplementedException();

    /// <summary>
    /// Applies <paramref name="binder"/> to the inner value when <c>Some</c>,
    /// propagating its result; otherwise returns <c>None</c>.
    /// </summary>
    public Option<TResult> Bind<TResult>(Func<T, Option<TResult>> binder) => throw new NotImplementedException();

    /// <summary>
    /// Eliminates the option by applying one of two continuations depending on state.
    /// </summary>
    public TResult Match<TResult>(Func<T, TResult> some, Func<TResult> none) => throw new NotImplementedException();

    /// <summary>Returns the inner value when <c>Some</c>, or <paramref name="fallback"/> when <c>None</c>.</summary>
    public T GetValueOr(T fallback) => throw new NotImplementedException();

    /// <inheritdoc/>
    public override string ToString() => throw new NotImplementedException();
}
