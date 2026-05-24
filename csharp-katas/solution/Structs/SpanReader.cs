namespace Katas.Structs;

/// <summary>
/// A forward-only, stack-only cursor for reading binary data from a
/// <see cref="ReadOnlySpan{T}"/> of bytes.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why <c>ref struct</c>?</b>
/// <list type="bullet">
///   <item>
///     <description>
///       A <c>ref struct</c> can hold a <see cref="ReadOnlySpan{T}"/> field.
///       Ordinary structs and classes cannot, because <see cref="ReadOnlySpan{T}"/>
///       is itself a <c>ref struct</c> and is forbidden from living on the heap.
///     </description>
///   </item>
///   <item>
///     <description>
///       <b>Stack-only constraint:</b>  A <c>ref struct</c> may not be boxed, stored
///       in fields of a class or non-<c>ref struct</c>, used as a generic type argument,
///       captured in lambdas / async methods, or stored in arrays.  These restrictions
///       guarantee that the value never escapes to the heap, which is essential because
///       the underlying span memory may be stack-allocated or may have a narrower
///       lifetime than the heap.
///     </description>
///   </item>
///   <item>
///     <description>
///       <b>Zero-allocation reads:</b>  All reads advance a position counter inside the
///       struct; no intermediate buffers are allocated.
///     </description>
///   </item>
/// </list>
/// </para>
/// <para>
/// <b>Little-endian layout:</b>  <see cref="ReadInt32LittleEndian"/> reads four bytes
/// in little-endian order (least significant byte first), which is the native byte
/// order on x86/x64.  For big-endian data, reverse the shift offsets.
/// </para>
/// </remarks>
public ref struct SpanReader
{
    private readonly ReadOnlySpan<byte> _buffer;

    /// <summary>Gets the current read position (byte offset from the beginning).</summary>
    public int Position { get; private set; }

    /// <summary>Gets the number of unread bytes remaining.</summary>
    public int Remaining => _buffer.Length - Position;

    /// <summary>
    /// Initialises a new <see cref="SpanReader"/> positioned at the start of
    /// <paramref name="buffer"/>.
    /// </summary>
    /// <param name="buffer">The source byte span to read from.</param>
    public SpanReader(ReadOnlySpan<byte> buffer)
    {
        _buffer = buffer;
        Position = 0;
    }

    // -------------------------------------------------------------------------
    // Read methods
    // -------------------------------------------------------------------------

    /// <summary>
    /// Reads and returns the next byte, advancing <see cref="Position"/> by one.
    /// </summary>
    /// <returns>The byte at the current position.</returns>
    /// <exception cref="InvalidOperationException">
    /// There are no more bytes to read (<see cref="Remaining"/> is zero).
    /// </exception>
    public byte ReadByte()
    {
        if (Remaining < 1)
            throw new InvalidOperationException("Cannot read past end of buffer.");

        return _buffer[Position++];
    }

    /// <summary>
    /// Reads a signed 32-bit integer stored in little-endian byte order (4 bytes),
    /// advancing <see cref="Position"/> by four.
    /// </summary>
    /// <returns>The decoded <see cref="int"/> value.</returns>
    /// <exception cref="InvalidOperationException">
    /// Fewer than four bytes remain in the buffer.
    /// </exception>
    /// <remarks>
    /// Little-endian layout: byte[0] is the least-significant byte (bits 0–7),
    /// byte[3] is the most-significant byte (bits 24–31).
    /// This matches the native memory layout on x86/x64 and the encoding used by
    /// most binary file formats on Windows.
    /// </remarks>
    public int ReadInt32LittleEndian()
    {
        if (Remaining < 4)
            throw new InvalidOperationException("Cannot read 4 bytes: insufficient data.");

        var b0 = _buffer[Position];
        var b1 = _buffer[Position + 1];
        var b2 = _buffer[Position + 2];
        var b3 = _buffer[Position + 3];
        Position += 4;

        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /// <summary>
    /// Attempts to read the next byte without throwing on end-of-buffer.
    /// </summary>
    /// <param name="value">
    /// When <c>true</c> is returned, contains the byte that was read.
    /// When <c>false</c>, the value is zero and <see cref="Position"/> is unchanged.
    /// </param>
    /// <returns><c>true</c> if a byte was available; <c>false</c> if the buffer is exhausted.</returns>
    public bool TryReadByte(out byte value)
    {
        if (Remaining < 1) { value = 0; return false; }
        value = _buffer[Position++];
        return true;
    }
}
