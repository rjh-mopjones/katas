using System.Diagnostics.CodeAnalysis;

namespace Katas.LockFreeStack;

/// <summary>
/// A lock-free, thread-safe LIFO stack implemented as a Treiber stack.
/// </summary>
/// <typeparam name="T">Element type.</typeparam>
public sealed class LockFreeStack<T>
{
    /// <summary>Gets a value indicating whether the stack contains no elements.</summary>
    public bool IsEmpty => throw new NotImplementedException();

    /// <summary>
    /// Pushes <paramref name="item"/> onto the top of the stack.
    /// </summary>
    /// <param name="item">The value to push.</param>
    public void Push(T item) => throw new NotImplementedException();

    /// <summary>
    /// Attempts to remove and return the top element of the stack.
    /// </summary>
    /// <param name="item">
    /// When this method returns <see langword="true"/>, the popped value;
    /// <see langword="default"/> otherwise.
    /// </param>
    /// <returns><see langword="true"/> if an element was popped; <see langword="false"/> if empty.</returns>
    public bool TryPop([MaybeNullWhen(false)] out T item)
    {
        throw new NotImplementedException();
    }
}
