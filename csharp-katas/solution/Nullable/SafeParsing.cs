using System.Diagnostics.CodeAnalysis;

namespace Katas.Nullable;

/// <summary>
/// A collection of nullable-reference-type–annotated helper utilities that demonstrate
/// how NRT flow-analysis attributes teach the compiler about null contracts at API boundaries.
/// </summary>
/// <remarks>
/// <para>
/// <b>Background — nullable reference type (NRT) analysis:</b>
/// With <c>#nullable enable</c> the C# compiler tracks whether a reference may be null by flowing
/// information through assignments and conditions.  However, the compiler cannot always infer the
/// correct nullability contract from a method signature alone: it is conservative and assumes
/// the worst.  The attributes in <see cref="System.Diagnostics.CodeAnalysis"/> let you assert a
/// stronger contract so the compiler can propagate the information further.
/// </para>
/// <para>
/// <b>Key attributes used here:</b>
/// <list type="bullet">
///   <item><description>
///     <c>[NotNullWhen(true)]</c> — when the method returns <c>true</c>, the annotated
///     <c>out</c> parameter is guaranteed non-null.  After <c>if (TryGetValue(map, key, out var v))</c>
///     the compiler knows <c>v</c> is non-null inside the <c>true</c> branch.
///   </description></item>
///   <item><description>
///     <c>[MaybeNullWhen(false)]</c> — the dual: when the method returns <c>false</c>, the
///     annotated <c>out</c> parameter may be null.  Used on <c>Dictionary.TryGetValue</c> in the BCL.
///   </description></item>
///   <item><description>
///     <c>[NotNullIfNotNull("param")]</c> — the return value is non-null whenever the named
///     input parameter is non-null.  Useful for pass-through helpers.
///   </description></item>
/// </list>
/// </para>
/// </remarks>
public static class SafeParsing
{
    /// <summary>
    /// Attempts to look up <paramref name="key"/> in <paramref name="map"/>.
    /// </summary>
    /// <param name="map">The dictionary to query.</param>
    /// <param name="key">The key to look up.</param>
    /// <param name="value">
    /// When this method returns <c>true</c>, contains the non-null value associated with
    /// <paramref name="key"/>.  When <c>false</c>, contains <c>null</c>.
    /// The <see cref="NotNullWhenAttribute"/> tells the compiler that inside a
    /// <c>if (TryGetValue(..., out var v))</c> branch, <c>v</c> is definitively non-null — no
    /// null-dereference warning will be issued.
    /// </param>
    /// <returns><c>true</c> if the key was found; <c>false</c> otherwise.</returns>
    public static bool TryGetValue(
        IReadOnlyDictionary<string, string> map,
        string key,
        [NotNullWhen(true)] out string? value)
    {
        return map.TryGetValue(key, out value);
    }

    /// <summary>
    /// Returns the first non-null element in <paramref name="candidates"/>, or
    /// <paramref name="fallback"/> if all candidates are null.
    /// </summary>
    /// <param name="fallback">
    /// The value to return when every candidate is null.  Must be non-null itself so the return
    /// type is unconditionally non-null.
    /// </param>
    /// <param name="candidates">Zero or more nullable strings to examine in order.</param>
    /// <returns>The first non-null candidate, or <paramref name="fallback"/>.</returns>
    /// <remarks>
    /// The return type is <c>string</c> (not <c>string?</c>) because either a candidate or the
    /// fallback is always returned, and the fallback is declared non-null by the caller.
    /// The compiler confirms this because <paramref name="fallback"/> is a non-nullable parameter.
    /// </remarks>
    public static string FirstNonNullOrDefault(string fallback, params string?[] candidates)
    {
        foreach (string? candidate in candidates)
        {
            if (candidate is not null)
                return candidate;
        }
        return fallback;
    }

    /// <summary>
    /// Returns the length of <paramref name="s"/>, or zero if <paramref name="s"/> is null.
    /// </summary>
    /// <param name="s">The string whose length to measure; may be null.</param>
    /// <returns>
    /// <c>s.Length</c> when <paramref name="s"/> is non-null, otherwise <c>0</c>.
    /// </returns>
    /// <remarks>
    /// <b>Null-flow analysis:</b> After the pattern <c>if (s is null) return 0;</c> the compiler
    /// narrows the type of <c>s</c> to non-null in the remainder of the method body — no cast or
    /// <c>!</c> operator needed.  This is the NRT flow analysis working as intended.
    /// </remarks>
    public static int LengthOrZero(string? s)
    {
        if (s is null) return 0;
        return s.Length; // compiler knows s is non-null here
    }

    /// <summary>
    /// Concatenates <paramref name="a"/> and <paramref name="b"/>, treating either null as an
    /// empty string.
    /// </summary>
    /// <param name="a">First string; may be null.</param>
    /// <param name="b">Second string; may be null.</param>
    /// <returns>A non-null concatenation (possibly empty).</returns>
    /// <remarks>
    /// The return type is <c>string</c> (not <c>string?</c>) because <see cref="string.Concat(string?, string?)"/>
    /// returns a non-null result (the BCL itself annotates it <c>[return: NotNull]</c>).
    /// This is a simple demonstration of null-safe composition that always yields a non-null result.
    /// </remarks>
    public static string Combine(string? a, string? b)
    {
        return string.Concat(a, b);
    }
}
