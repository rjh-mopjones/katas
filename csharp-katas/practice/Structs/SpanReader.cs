namespace Katas.Structs;

/// <summary>
/// A forward-only, stack-only cursor for reading binary data from a
/// <see cref="ReadOnlySpan{T}"/> of bytes.
/// </summary>
public ref struct SpanReader
{
    /// <summary>Gets the current read position (byte offset from the beginning).</summary>
    public int Position { get; private set; }

    /// <summary>Gets the number of unread bytes remaining.</summary>
    public int Remaining { get { throw new NotImplementedException(); } }

    /// <summary>Initialises a new <see cref="SpanReader"/> positioned at the start of <paramref name="buffer"/>.</summary>
    public SpanReader(ReadOnlySpan<byte> buffer) { throw new NotImplementedException(); }

    /// <summary>Reads and returns the next byte, advancing <see cref="Position"/> by one.</summary>
    public byte ReadByte() { throw new NotImplementedException(); }

    /// <summary>
    /// Reads a signed 32-bit integer stored in little-endian byte order (4 bytes),
    /// advancing <see cref="Position"/> by four.
    /// </summary>
    public int ReadInt32LittleEndian() { throw new NotImplementedException(); }

    /// <summary>Attempts to read the next byte without throwing on end-of-buffer.</summary>
    public bool TryReadByte(out byte value) { throw new NotImplementedException(); }
}
