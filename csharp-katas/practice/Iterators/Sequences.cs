namespace Katas.Iterators;

/// <summary>
/// Factory methods for lazily-evaluated, potentially infinite sequences.
/// </summary>
public static class Sequences
{
    /// <summary>
    /// Generates the infinite Fibonacci sequence: 0, 1, 1, 2, 3, 5, 8, …
    /// Use <c>.Take(n)</c> to bound the output.
    /// </summary>
    public static IEnumerable<long> Fibonacci() => throw new NotImplementedException();

    /// <summary>
    /// Generates the infinite sequence of natural numbers starting at 1: 1, 2, 3, …
    /// Use <c>.Take(n)</c> to bound the output.
    /// </summary>
    public static IEnumerable<int> Naturals() => throw new NotImplementedException();

    /// <summary>
    /// Infinitely cycles through the elements of <paramref name="items"/>.
    /// Use <c>.Take(n)</c> to bound the output.
    /// </summary>
    /// <exception cref="ArgumentNullException"><paramref name="items"/> is <c>null</c>.</exception>
    /// <exception cref="ArgumentException"><paramref name="items"/> is empty.</exception>
    public static IEnumerable<T> Cycle<T>(IReadOnlyList<T> items) => throw new NotImplementedException();
}
