namespace Katas.Generics;

/// <summary>
/// A discriminated-union value type that either holds a value (<c>Some</c>) or nothing (<c>None</c>).
/// </summary>
/// <typeparam name="T">
/// The type of the wrapped value.  Works for both value types and reference types because
/// the presence/absence of a value is tracked by <see cref="IsSome"/>, not by null-ness.
/// This means <c>Option&lt;int&gt;</c> and <c>Option&lt;string?&gt;</c> are both well-formed and
/// distinguishable: <c>None</c> is structurally different from <c>Some(null)</c>.
/// </typeparam>
/// <remarks>
/// <para>
/// <b>Why a struct?</b>  Allocating a heap object for every <c>Some</c> is wasteful on hot paths.
/// A <c>readonly struct</c> copies on assignment but is zero-allocation and stack-friendly.
/// Trade-off: boxing occurs when the struct is assigned to an interface or <c>object</c>.
/// </para>
/// <para>
/// <b>Monadic laws:</b>
/// <list type="bullet">
///   <item><description>Left identity:  <c>Option.Some(x).Bind(f) == f(x)</c></description></item>
///   <item><description>Right identity: <c>m.Bind(Option.Some) == m</c></description></item>
///   <item><description>Associativity:  <c>m.Bind(f).Bind(g) == m.Bind(x => f(x).Bind(g))</c></description></item>
/// </list>
/// </para>
/// <para>
/// <b>Alternative designs:</b> A class-based DU (abstract base + two sealed subclasses) avoids
/// the boxing issue but costs a heap allocation per <c>Some</c>.  F# uses a class for this reason.
/// </para>
/// </remarks>
public readonly struct Option<T>
{
    private readonly T _value;

    /// <summary>
    /// <c>true</c> when this instance wraps a value; <c>false</c> for <c>None</c>.
    /// </summary>
    public bool IsSome { get; }

    // Private constructor: callers use the static factory members.
    private Option(T value)
    {
        _value = value;
        IsSome = true;
    }

    /// <summary>
    /// Creates a <c>Some</c> wrapping <paramref name="value"/>.
    /// </summary>
    /// <param name="value">
    /// The value to wrap.  For reference types this may legally be <c>null</c>; the caller is
    /// responsible for deciding whether that is meaningful in their domain.
    /// </param>
    public static Option<T> Some(T value) => new(value);

    /// <summary>
    /// The singleton <c>None</c> for this <typeparamref name="T"/>.
    /// </summary>
    /// <remarks>
    /// The default value of any struct (all fields zero/null) is <c>None</c> because
    /// <see cref="IsSome"/> defaults to <c>false</c> and <c>_value</c> defaults to
    /// <c>default(T)</c>.  The explicit <c>static readonly</c> field just gives a readable name.
    /// </remarks>
    public static readonly Option<T> None = default;

    /// <summary>
    /// Transforms the inner value with <paramref name="selector"/> when <c>Some</c>,
    /// otherwise propagates <c>None</c>.
    /// </summary>
    /// <typeparam name="TResult">The type produced by the selector.</typeparam>
    /// <param name="selector">Pure function applied to the wrapped value.</param>
    /// <returns><c>Some(selector(value))</c> or <c>None</c>.</returns>
    /// <remarks>
    /// <b>Map vs Bind:</b> Use <c>Map</c> when the selector cannot fail; use <c>Bind</c> when the
    /// selector itself may return <c>None</c>.  <c>Map(f)</c> is equivalent to
    /// <c>Bind(x => Option&lt;TResult&gt;.Some(f(x)))</c>.
    /// </remarks>
    public Option<TResult> Map<TResult>(Func<T, TResult> selector)
    {
        if (!IsSome) return Option<TResult>.None;
        return Option<TResult>.Some(selector(_value));
    }

    /// <summary>
    /// Applies <paramref name="binder"/> to the inner value when <c>Some</c>,
    /// propagating its result (which may itself be <c>None</c>); otherwise returns <c>None</c>.
    /// </summary>
    /// <typeparam name="TResult">Element type of the returned option.</typeparam>
    /// <param name="binder">A function from <typeparamref name="T"/> to <c>Option&lt;TResult&gt;</c>.</param>
    /// <remarks>
    /// <b>Flat-map / SelectMany:</b> This is the monadic bind (<c>&gt;&gt;=</c>) operation.
    /// It avoids nested <c>Option&lt;Option&lt;T&gt;&gt;</c> by flattening one level.
    /// </remarks>
    public Option<TResult> Bind<TResult>(Func<T, Option<TResult>> binder)
    {
        if (!IsSome) return Option<TResult>.None;
        return binder(_value);
    }

    /// <summary>
    /// Eliminates the option by applying one of two continuations depending on state.
    /// </summary>
    /// <typeparam name="TResult">The return type of both continuations.</typeparam>
    /// <param name="some">Called with the inner value when <c>IsSome</c>.</param>
    /// <param name="none">Called (no argument) when <c>None</c>.</param>
    /// <returns>The result of whichever continuation was invoked.</returns>
    /// <remarks>
    /// <b>Pattern-matching alternative:</b> C# switch expressions work on structs but require
    /// accessing <see cref="IsSome"/> explicitly.  <c>Match</c> encapsulates that branching
    /// and is the preferred way to consume an <c>Option</c> in a functional style.
    /// </remarks>
    public TResult Match<TResult>(Func<T, TResult> some, Func<TResult> none)
    {
        return IsSome ? some(_value) : none();
    }

    /// <summary>
    /// Returns the inner value when <c>Some</c>, or <paramref name="fallback"/> when <c>None</c>.
    /// </summary>
    /// <param name="fallback">Value to use when the option is empty.</param>
    public T GetValueOr(T fallback) => IsSome ? _value : fallback;

    /// <inheritdoc/>
    public override string ToString() =>
        IsSome ? $"Some({_value})" : "None";
}
