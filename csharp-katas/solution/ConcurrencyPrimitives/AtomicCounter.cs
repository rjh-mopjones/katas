namespace Katas.ConcurrencyPrimitives;

/// <summary>
/// A thread-safe counter backed by a single <see cref="long"/> field, using only
/// <see cref="System.Threading.Interlocked"/> operations — no lock, no <c>volatile</c> keyword.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why Interlocked?</b>  CPU memory models allow reads/writes to be reordered or cached in
/// registers.  <c>Interlocked</c> operations issue full memory fences, guaranteeing that every
/// thread sees the latest value and that the read-modify-write is atomic (no torn reads on
/// 64-bit platforms where <c>long</c> could otherwise be split across two 32-bit bus cycles
/// on x86-32).
/// </para>
/// <para>
/// <b>CAS loop in <see cref="TryIncrementIfBelow"/>:</b>  Compare-and-Swap (CAS) is the
/// primitive that makes lock-free algorithms possible.  The loop reads the current value,
/// computes the desired new value, and calls <c>Interlocked.CompareExchange</c>.
/// CAS atomically does: if <c>_value == comparand</c> then swap in <c>newValue</c> and return
/// the old value.  If another thread raced us and changed <c>_value</c> between our read and
/// our CAS the returned value will differ from <c>comparand</c>; we retry.  This is
/// <em>optimistic concurrency</em>: we assume no contention and pay a retry cost only on conflict.
/// </para>
/// <para>
/// <b>ABA problem:</b>  A classic CAS pitfall — thread A reads value 5, thread B increments
/// to 6 then decrements back to 5, thread A's CAS succeeds even though the state changed
/// beneath it.  For a monotonically-incrementing counter this is harmless because the value
/// never decreases; CAS on the same value means the counter is logically unchanged.
/// </para>
/// <para>
/// <b>Alternative — <c>lock</c>:</b>  Simpler to reason about, but introduces kernel-level
/// context switches under contention.  <c>Interlocked</c> loops stay entirely in user space
/// and are optimal for low-contention, short critical sections like a counter.
/// </para>
/// </remarks>
public sealed class AtomicCounter
{
    private long _value;

    /// <summary>Gets the current counter value.  Uses <see cref="Interlocked.Read"/> to
    /// guarantee a coherent 64-bit read on 32-bit runtimes.</summary>
    public long Value => Interlocked.Read(ref _value);

    /// <summary>Atomically increments the counter by one and returns the new value.</summary>
    public long Increment() => Interlocked.Increment(ref _value);

    /// <summary>Atomically adds <paramref name="delta"/> to the counter and returns the new value.</summary>
    /// <param name="delta">Amount to add.  May be negative (acts as a decrement).</param>
    public long Add(long delta) => Interlocked.Add(ref _value, delta);

    /// <summary>
    /// Atomically increments the counter by one if, and only if, the current value is strictly
    /// less than <paramref name="max"/>.  Uses a CAS retry loop so no permits are lost under
    /// heavy concurrent contention.
    /// </summary>
    /// <param name="max">Upper bound (exclusive).  The counter will never exceed this value.</param>
    /// <returns>
    /// <see langword="true"/> if the counter was incremented; <see langword="false"/> if the
    /// current value was already ≥ <paramref name="max"/>.
    /// </returns>
    /// <remarks>
    /// The loop is: read → check → CAS.  On a CAS miss (another thread changed the value) we
    /// re-read and retry, so the method is wait-free in the absence of starvation and
    /// lock-free (system-wide progress) in general.
    /// </remarks>
    public bool TryIncrementIfBelow(long max)
    {
        while (true)
        {
            long current = Interlocked.Read(ref _value);
            if (current >= max) return false;

            long updated = current + 1;
            long seen = Interlocked.CompareExchange(ref _value, updated, current);
            if (seen == current) return true;
            // Another thread changed _value between our read and our CAS; retry.
        }
    }
}
