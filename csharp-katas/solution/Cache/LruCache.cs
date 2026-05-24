using System.Diagnostics.CodeAnalysis;

namespace Katas.Cache;

/// <summary>
/// A Least-Recently-Used (LRU) cache with O(1) <see cref="TryGet"/> and <see cref="Put"/>
/// operations, backed by a <see cref="Dictionary{TKey, TValue}"/> and a
/// <see cref="LinkedList{T}"/>.
/// </summary>
/// <typeparam name="TKey">Key type.  Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
/// <remarks>
/// <para>
/// <b>Why Dictionary + LinkedList gives O(1):</b>
/// <list type="bullet">
///   <item><description>
///     The <see cref="LinkedList{T}"/> (BCL's doubly-linked list) keeps entries ordered from
///     most-recently-used (head) to least-recently-used (tail).  Moving a node to the head
///     (<c>Remove</c> + <c>AddFirst</c>) and removing the tail (<c>RemoveLast</c>) are both
///     O(1) on a doubly-linked list — no linear scan needed.
///   </description></item>
///   <item><description>
///     The <see cref="Dictionary{TKey,TValue}"/> maps each key to its
///     <see cref="LinkedListNode{T}"/> in the list, so lookups are O(1) and we can obtain the
///     node directly (no traversal).
///   </description></item>
/// </list>
/// Together: a get/put can locate the node in O(1) via the dictionary, then re-link it to
/// the head in O(1) via the doubly-linked list.  A plain <c>List&lt;T&gt;</c> would require
/// O(n) to search for the node on every access.
/// </para>
/// <para>
/// <b>This implementation is not thread-safe.</b>  See <see cref="ConcurrentLruCache{TKey,TValue}"/>
/// for a thread-safe wrapper.
/// </para>
/// </remarks>
public sealed class LruCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    private readonly int _capacity;
    private readonly Dictionary<TKey, LinkedListNode<(TKey Key, TValue Value)>> _map;
    private readonly LinkedList<(TKey Key, TValue Value)> _list = new();

    /// <summary>
    /// Initialises the cache.
    /// </summary>
    /// <param name="capacity">Maximum number of entries.  Must be at least 1.</param>
    /// <exception cref="ArgumentOutOfRangeException"><paramref name="capacity"/> is less than 1.</exception>
    public LruCache(int capacity)
    {
        if (capacity < 1) throw new ArgumentOutOfRangeException(nameof(capacity), capacity, "Capacity must be at least 1.");
        _capacity = capacity;
        _map = new Dictionary<TKey, LinkedListNode<(TKey, TValue)>>(capacity);
    }

    /// <inheritdoc/>
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value)
    {
        if (!_map.TryGetValue(key, out var node))
        {
            value = default;
            return false;
        }

        // Promote to most-recently-used (head).
        _list.Remove(node);
        _list.AddFirst(node);
        value = node.Value.Value;
        return true;
    }

    /// <inheritdoc/>
    public void Put(TKey key, TValue value)
    {
        if (_map.TryGetValue(key, out var existing))
        {
            // Update value and promote to head.
            _list.Remove(existing);
            _map.Remove(key);
        }
        else if (_list.Count == _capacity)
        {
            // Evict the least-recently-used entry (tail).
            var lru = _list.Last!;
            _list.RemoveLast();
            _map.Remove(lru.Value.Key);
        }

        var node = new LinkedListNode<(TKey, TValue)>((key, value));
        _list.AddFirst(node);
        _map[key] = node;
    }

    /// <inheritdoc/>
    public int Count => _list.Count;
}
