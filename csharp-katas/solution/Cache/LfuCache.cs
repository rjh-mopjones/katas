using System.Diagnostics.CodeAnalysis;

namespace Katas.Cache;

/// <summary>
/// A Least-Frequently-Used (LFU) cache with O(1) <see cref="TryGet"/> and <see cref="Put"/>
/// operations, using the frequency-bucket + <c>minFreq</c> algorithm.
/// </summary>
/// <typeparam name="TKey">Key type.  Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
/// <remarks>
/// <para>
/// <b>Data structures:</b>
/// <list type="bullet">
///   <item><description>
///     <c>_keyMap</c> — maps each key to its <see cref="Entry"/> (value + frequency).
///   </description></item>
///   <item><description>
///     <c>_freqMap</c> — maps each frequency to an ordered set of keys at that frequency,
///     implemented as a <see cref="LinkedList{T}"/> (insertion-order = LRU order within
///     the same frequency) plus a parallel <see cref="Dictionary{TKey,TValue}"/> of
///     <see cref="LinkedListNode{T}"/> pointers for O(1) removal.
///   </description></item>
///   <item><description>
///     <c>_minFreq</c> — the current minimum frequency across all live entries.
///   </description></item>
/// </list>
/// </para>
/// <para>
/// <b>minFreq maintenance:</b>
/// <list type="bullet">
///   <item><description>
///     On <b>get/put of an existing key</b>: the key moves from bucket <c>f</c> to
///     <c>f+1</c>.  If bucket <c>f</c> is now empty and <c>f == _minFreq</c>, increment
///     <c>_minFreq</c>.  It cannot jump by more than 1 in this case.
///   </description></item>
///   <item><description>
///     On <b>insert of a new key</b>: the new entry always starts at frequency 1, so
///     <c>_minFreq</c> is reset to 1 (the new entry is the least frequent).
///   </description></item>
///   <item><description>
///     On <b>eviction</b>: remove from bucket <c>_minFreq</c>, taking the LRU entry
///     (tail of the linked list) as the victim.
///   </description></item>
/// </list>
/// Each of these is O(1), giving O(1) overall for both operations.
/// </para>
/// <para>
/// <b>Tie-breaking:</b> Within the same frequency, the LRU entry (oldest access) is evicted
/// first.  This is achieved by using the linked list as an insertion-ordered set — new/promoted
/// keys are added to the head; eviction removes from the tail.
/// </para>
/// </remarks>
public sealed class LfuCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    private sealed class Entry(TValue value, int freq)
    {
        public TValue Value = value;
        public int Freq = freq;
    }

    private sealed class FreqBucket
    {
        public readonly LinkedList<TKey> Order = new();
        public readonly Dictionary<TKey, LinkedListNode<TKey>> Nodes = new();
    }

    private readonly int _capacity;
    private readonly Dictionary<TKey, Entry> _keyMap = new();
    private readonly Dictionary<int, FreqBucket> _freqMap = new();
    private int _minFreq;

    /// <summary>
    /// Initialises the cache.
    /// </summary>
    /// <param name="capacity">Maximum number of entries.  Must be at least 1.</param>
    /// <exception cref="ArgumentOutOfRangeException"><paramref name="capacity"/> is less than 1.</exception>
    public LfuCache(int capacity)
    {
        if (capacity < 1) throw new ArgumentOutOfRangeException(nameof(capacity), capacity, "Capacity must be at least 1.");
        _capacity = capacity;
    }

    /// <inheritdoc/>
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value)
    {
        if (!_keyMap.TryGetValue(key, out Entry? entry))
        {
            value = default;
            return false;
        }

        IncrementFreq(key, entry);
        value = entry.Value;
        return true;
    }

    /// <inheritdoc/>
    public void Put(TKey key, TValue value)
    {
        if (_capacity == 0) return;

        if (_keyMap.TryGetValue(key, out Entry? existing))
        {
            existing.Value = value;
            IncrementFreq(key, existing);
            return;
        }

        if (_keyMap.Count == _capacity)
            Evict();

        var entry = new Entry(value, 1);
        _keyMap[key] = entry;
        AddToFreqBucket(key, 1);
        _minFreq = 1; // New entry always starts at frequency 1.
    }

    /// <inheritdoc/>
    public int Count => _keyMap.Count;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void IncrementFreq(TKey key, Entry entry)
    {
        int oldFreq = entry.Freq;
        RemoveFromFreqBucket(key, oldFreq);

        // Advance minFreq if the bucket we just vacated was the minimum.
        if (oldFreq == _minFreq && !_freqMap.ContainsKey(oldFreq))
            _minFreq++;

        int newFreq = oldFreq + 1;
        entry.Freq = newFreq;
        AddToFreqBucket(key, newFreq);
    }

    private void Evict()
    {
        FreqBucket bucket = _freqMap[_minFreq];
        // Evict the LRU entry within this frequency bucket (tail of the linked list).
        TKey victim = bucket.Order.Last!.Value;
        RemoveFromFreqBucket(victim, _minFreq);
        _keyMap.Remove(victim);
    }

    private void AddToFreqBucket(TKey key, int freq)
    {
        if (!_freqMap.TryGetValue(freq, out FreqBucket? bucket))
        {
            bucket = new FreqBucket();
            _freqMap[freq] = bucket;
        }
        // Most-recently-used entries go to the head; LRU victim is the tail.
        bucket.Nodes[key] = bucket.Order.AddFirst(key);
    }

    private void RemoveFromFreqBucket(TKey key, int freq)
    {
        FreqBucket bucket = _freqMap[freq];
        bucket.Order.Remove(bucket.Nodes[key]);
        bucket.Nodes.Remove(key);
        if (bucket.Order.Count == 0)
            _freqMap.Remove(freq);
    }
}
