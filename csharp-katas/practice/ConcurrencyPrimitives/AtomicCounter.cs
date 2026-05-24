namespace Katas.ConcurrencyPrimitives;

/// <summary>
/// A thread-safe counter backed by a single <see cref="long"/> field, using only
/// <see cref="System.Threading.Interlocked"/> operations — no lock, no <c>volatile</c> keyword.
/// </summary>
public sealed class AtomicCounter
{
    /// <summary>Gets the current counter value.</summary>
    public long Value => throw new NotImplementedException();

    /// <summary>Atomically increments the counter by one and returns the new value.</summary>
    public long Increment() => throw new NotImplementedException();

    /// <summary>Atomically adds <paramref name="delta"/> to the counter and returns the new value.</summary>
    /// <param name="delta">Amount to add. May be negative (acts as a decrement).</param>
    public long Add(long delta) => throw new NotImplementedException();

    /// <summary>
    /// Atomically increments the counter by one if, and only if, the current value is strictly
    /// less than <paramref name="max"/>. Uses a CAS retry loop.
    /// </summary>
    /// <param name="max">Upper bound (exclusive).</param>
    /// <returns><see langword="true"/> if incremented; <see langword="false"/> if already ≥ <paramref name="max"/>.</returns>
    public bool TryIncrementIfBelow(long max) => throw new NotImplementedException();
}
