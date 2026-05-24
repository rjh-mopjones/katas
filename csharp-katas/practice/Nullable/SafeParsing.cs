using System.Diagnostics.CodeAnalysis;

namespace Katas.Nullable;

/// <summary>
/// A collection of nullable-reference-type–annotated helper utilities that demonstrate
/// how NRT flow-analysis attributes teach the compiler about null contracts at API boundaries.
/// </summary>
public static class SafeParsing
{
    /// <summary>
    /// Attempts to look up <paramref name="key"/> in <paramref name="map"/>.
    /// When true is returned, <paramref name="value"/> is guaranteed non-null.
    /// </summary>
    public static bool TryGetValue(
        IReadOnlyDictionary<string, string> map,
        string key,
        [NotNullWhen(true)] out string? value)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Returns the first non-null element in <paramref name="candidates"/>, or
    /// <paramref name="fallback"/> if all candidates are null.
    /// </summary>
    public static string FirstNonNullOrDefault(string fallback, params string?[] candidates) => throw new NotImplementedException();

    /// <summary>Returns the length of <paramref name="s"/>, or zero if <paramref name="s"/> is null.</summary>
    public static int LengthOrZero(string? s) => throw new NotImplementedException();

    /// <summary>
    /// Concatenates <paramref name="a"/> and <paramref name="b"/>, treating either null as empty string.
    /// </summary>
    public static string Combine(string? a, string? b) => throw new NotImplementedException();
}
