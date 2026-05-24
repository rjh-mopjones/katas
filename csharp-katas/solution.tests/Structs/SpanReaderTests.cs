namespace Katas.Tests.Structs;

using Katas.Structs;

public sealed class SpanReaderTests
{
    // -------------------------------------------------------------------------
    // ReadByte
    // -------------------------------------------------------------------------

    [Fact]
    public void ReadByte_Should_ReturnFirstByte_AndAdvancePosition()
    {
        byte[] data = [0x01, 0x02, 0x03];
        var reader = new SpanReader(data);

        var b = reader.ReadByte();

        Assert.Equal(0x01, b);
        Assert.Equal(1, reader.Position);
    }

    [Fact]
    public void ReadByte_Should_ReadSequentialBytes()
    {
        byte[] data = [10, 20, 30];
        var reader = new SpanReader(data);

        Assert.Equal(10,  reader.ReadByte());
        Assert.Equal(20,  reader.ReadByte());
        Assert.Equal(30,  reader.ReadByte());
        Assert.Equal(3,   reader.Position);
    }

    [Fact]
    public void ReadByte_Should_Throw_WhenBufferExhausted()
    {
        // SpanReader is a ref struct, so it cannot be captured in a lambda for Assert.Throws;
        // assert the throw via try/catch instead.
        var threw = false;
        try
        {
            var reader = new SpanReader([]); // empty
            reader.ReadByte();
        }
        catch (InvalidOperationException)
        {
            threw = true;
        }

        Assert.True(threw);
    }

    // -------------------------------------------------------------------------
    // TryReadByte
    // -------------------------------------------------------------------------

    [Fact]
    public void TryReadByte_Should_ReturnTrue_WhenBytesAvailable()
    {
        byte[] data = [0xFF];
        var reader = new SpanReader(data);

        var ok = reader.TryReadByte(out var value);

        Assert.True(ok);
        Assert.Equal(0xFF, value);
    }

    [Fact]
    public void TryReadByte_Should_ReturnFalse_WhenExhausted()
    {
        var reader = new SpanReader([]);
        var ok = reader.TryReadByte(out var value);

        Assert.False(ok);
        Assert.Equal(0, value);
    }

    [Fact]
    public void TryReadByte_Should_NotAdvancePosition_WhenExhausted()
    {
        byte[] data = [1];
        var reader = new SpanReader(data);
        reader.ReadByte(); // exhaust buffer

        var posBefore = reader.Position;
        reader.TryReadByte(out _);

        Assert.Equal(posBefore, reader.Position);
    }

    // -------------------------------------------------------------------------
    // ReadInt32LittleEndian
    // -------------------------------------------------------------------------

    [Fact]
    public void ReadInt32LittleEndian_Should_DecodeSmallPositiveValue()
    {
        // 305419896 = 0x12345678
        // little-endian bytes: 0x78, 0x56, 0x34, 0x12
        byte[] data = [0x78, 0x56, 0x34, 0x12];
        var reader = new SpanReader(data);

        var value = reader.ReadInt32LittleEndian();

        Assert.Equal(0x12345678, value);
        Assert.Equal(4, reader.Position);
    }

    [Fact]
    public void ReadInt32LittleEndian_Should_DecodeZero()
    {
        byte[] data = [0x00, 0x00, 0x00, 0x00];
        var reader = new SpanReader(data);

        Assert.Equal(0, reader.ReadInt32LittleEndian());
    }

    [Fact]
    public void ReadInt32LittleEndian_Should_DecodeNegativeValue()
    {
        // -1 in two's complement is 0xFFFFFFFF
        byte[] data = [0xFF, 0xFF, 0xFF, 0xFF];
        var reader = new SpanReader(data);

        Assert.Equal(-1, reader.ReadInt32LittleEndian());
    }

    [Fact]
    public void ReadInt32LittleEndian_Should_Throw_WhenFewerThanFourBytesRemain()
    {
        byte[] data = [0x01, 0x02, 0x03]; // only 3 bytes

        // ref struct cannot be captured in a lambda; assert via try/catch.
        var threw = false;
        try
        {
            var reader = new SpanReader(data);
            reader.ReadInt32LittleEndian();
        }
        catch (InvalidOperationException)
        {
            threw = true;
        }

        Assert.True(threw);
    }

    // -------------------------------------------------------------------------
    // Sequential read of mixed types
    // -------------------------------------------------------------------------

    [Fact]
    public void Reader_Should_ReadByteAndInt32Sequentially()
    {
        // Layout: [1 byte: 0xAB] [4 bytes: 42 little-endian] [1 byte: 0xCD]
        byte[] data =
        [
            0xAB,
            42, 0x00, 0x00, 0x00,
            0xCD,
        ];
        var reader = new SpanReader(data);

        var first  = reader.ReadByte();
        var middle = reader.ReadInt32LittleEndian();
        var last   = reader.ReadByte();

        Assert.Equal(0xAB, first);
        Assert.Equal(42,   middle);
        Assert.Equal(0xCD, last);
        Assert.Equal(0,    reader.Remaining);
    }
}
