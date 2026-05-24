namespace Katas.Iterators;

/// <summary>
/// A hand-rolled enumerable that counts from <see cref="From"/> down to 1,
/// implementing both <see cref="IEnumerable{T}"/> and <see cref="IEnumerator{T}"/> on the same object.
/// </summary>
public sealed class CountdownSequence : IEnumerable<int>, IEnumerator<int>
{
    /// <summary>The inclusive upper bound supplied at construction time.</summary>
    public int From { get; }

    /// <summary>Initialises a new countdown sequence starting at <paramref name="from"/>.</summary>
    public CountdownSequence(int from) => throw new NotImplementedException();

    // IEnumerable<int>

    /// <summary>
    /// Returns <c>this</c> on the first call (avoids an allocation); subsequent calls
    /// return a fresh armed instance.
    /// </summary>
    public IEnumerator<int> GetEnumerator() => throw new NotImplementedException();

    System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() =>
        GetEnumerator();

    // IEnumerator<int>

    /// <summary>The current countdown value. Undefined before the first <see cref="MoveNext"/> and after it returns <c>false</c>.</summary>
    public int Current => throw new NotImplementedException();

    object System.Collections.IEnumerator.Current => Current;

    /// <summary>Advances to the next countdown value. Returns <c>false</c> when the sequence is exhausted.</summary>
    public bool MoveNext() => throw new NotImplementedException();

    /// <summary>Resets the state machine so the sequence can be re-enumerated.</summary>
    public void Reset() => throw new NotImplementedException();

    /// <summary>Marks the enumerator finished so subsequent <see cref="MoveNext"/> calls return <c>false</c>.</summary>
    public void Dispose() => throw new NotImplementedException();
}
