using System.Diagnostics.CodeAnalysis;

namespace Katas.LockFreeStack;

/// <summary>
/// A lock-free, thread-safe LIFO stack implemented as a Treiber stack.
/// </summary>
/// <typeparam name="T">Element type.</typeparam>
/// <remarks>
/// <para>
/// <b>Treiber stack algorithm:</b>  The stack is represented as a singly-linked list whose
/// head pointer is stored in a single field <c>_head</c>.  All mutations go through a
/// <em>Compare-and-Swap (CAS) retry loop</em>:
/// <list type="number">
///   <item><description>Read the current <c>_head</c> into <c>oldHead</c>.</description></item>
///   <item><description>Build the desired new state (<c>newHead</c>).</description></item>
///   <item><description>Call <c>Interlocked.CompareExchange(ref _head, newHead, oldHead)</c>.
///     If <c>_head</c> still equals <c>oldHead</c>, the swap succeeds and we return.
///     If another thread changed <c>_head</c> in the meantime the returned value differs
///     from <c>oldHead</c> and we restart from step 1.</description></item>
/// </list>
/// This guarantees <em>lock-freedom</em>: at any point in time, <em>some</em> thread is
/// making forward progress — a slow or suspended thread cannot block others (unlike a lock,
/// where a sleeping lock-holder stops everyone).
/// </para>
/// <para>
/// <b>ABA problem on <see cref="TryPop"/>:</b>  The classic ABA scenario: thread A reads
/// head = node X (value 1) whose next is node Y (value 2).  Thread B pops X, pops Y, then
/// pushes a <em>new</em> node with the same address as X (e.g. from a free list) but next = null.
/// Thread A's CAS sees head == X and succeeds, but head.next is now null — Y is silently lost.
/// <br/>
/// In a GC'd runtime with fresh heap allocations there is <em>no free list</em>: each
/// <c>new Node</c> produces a unique object, so the same address cannot be reused while A's
/// reference to it is live.  Push is therefore ABA-safe.  Pop is also safe for the same
/// reason: once we hold a reference to <c>oldHead</c> the GC cannot reclaim it, so no new
/// node can land at that address.  Were we to maintain a custom free list we would need to
/// pair the pointer with a version stamp (<em>tagged pointer</em>) to detect ABA.
/// </para>
/// <para>
/// <b>Progress guarantees:</b>  Push is wait-free in practice (bounded retries if at most N
/// threads spin — N being the core count).  Pop is lock-free for the same reason.  Neither
/// operation ever blocks on a kernel synchronisation object.
/// </para>
/// </remarks>
public sealed class LockFreeStack<T>
{
    private sealed class Node(T value, Node? next)
    {
        public readonly T Value = value;
        public readonly Node? Next = next;
    }

    private Node? _head;

    /// <summary>Gets a value indicating whether the stack contains no elements.</summary>
    public bool IsEmpty => Volatile.Read(ref _head) is null;

    /// <summary>
    /// Pushes <paramref name="item"/> onto the top of the stack.
    /// </summary>
    /// <param name="item">The value to push.</param>
    /// <remarks>
    /// CAS loop: create a new node pointing to the current head, then atomically swing
    /// <c>_head</c> from the old head to the new node.
    /// </remarks>
    public void Push(T item)
    {
        while (true)
        {
            Node? oldHead = Volatile.Read(ref _head);
            var newHead = new Node(item, oldHead);
            if (Interlocked.CompareExchange(ref _head, newHead, oldHead) == oldHead)
                return;
        }
    }

    /// <summary>
    /// Attempts to remove and return the top element of the stack.
    /// </summary>
    /// <param name="item">
    /// When this method returns <see langword="true"/>, the popped value;
    /// <see langword="default"/> otherwise.
    /// </param>
    /// <returns>
    /// <see langword="true"/> if an element was popped; <see langword="false"/> if the stack
    /// was empty.
    /// </returns>
    /// <remarks>
    /// CAS loop: read the current head, confirm it is non-null, then atomically swing
    /// <c>_head</c> to the next node.  On CAS failure another thread raced us; retry.
    /// </remarks>
    public bool TryPop([MaybeNullWhen(false)] out T item)
    {
        while (true)
        {
            Node? oldHead = Volatile.Read(ref _head);
            if (oldHead is null)
            {
                item = default;
                return false;
            }

            if (Interlocked.CompareExchange(ref _head, oldHead.Next, oldHead) == oldHead)
            {
                item = oldHead.Value;
                return true;
            }
        }
    }
}
