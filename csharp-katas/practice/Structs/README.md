# Structs

Value types done right: `readonly struct` for `Vector2` and `ref struct` for `SpanReader`.

## The problem

Implement a 2D vector as a `readonly struct` with arithmetic operators and equality, then implement a forward-only binary reader as a `ref struct` that holds a `ReadOnlySpan<byte>` field — something only a `ref struct` can do.

## Requirements

**Vector2**
- Component-wise `+`, `-`, `*` (scalar) operators; symmetric scalar multiplication.
- `Dot(in Vector2 other)` product; `Length` property.
- Full value equality (`IEquatable<Vector2>`), `==`/`!=` operators, `GetHashCode`.
- `ToString()` returns `"(X, Y)"`.

**SpanReader**
- Must be declared `ref struct` — it holds a `ReadOnlySpan<byte>` field.
- `Position` tracks current byte offset; `Remaining` returns bytes left.
- `ReadByte()` reads one byte and advances; throws `InvalidOperationException` at end.
- `ReadInt32LittleEndian()` reads four bytes in little-endian order and advances; throws if fewer than 4 bytes remain.
- `TryReadByte(out byte value)` returns `false` without throwing when exhausted.

## What you implement

```csharp
public readonly struct Vector2 : IEquatable<Vector2>
{
    public double X { get; }
    public double Y { get; }
    public Vector2(double x, double y);
    public static Vector2 operator +(Vector2 a, Vector2 b);
    public static Vector2 operator -(Vector2 a, Vector2 b);
    public static Vector2 operator *(Vector2 v, double scalar);
    public static Vector2 operator *(double scalar, Vector2 v);
    public double Dot(in Vector2 other);
    public double Length { get; }
    public bool Equals(Vector2 other);
    public override bool Equals(object? obj);
    public override int GetHashCode();
    public static bool operator ==(Vector2 left, Vector2 right);
    public static bool operator !=(Vector2 left, Vector2 right);
    public override string ToString();
}

public ref struct SpanReader
{
    public int Position { get; }
    public int Remaining { get; }
    public SpanReader(ReadOnlySpan<byte> buffer);
    public byte ReadByte();
    public int ReadInt32LittleEndian();
    public bool TryReadByte(out byte value);
}
```

## The real challenge

- A `readonly struct` with throwing members compiles fine, but you cannot have non-nullable fields unassigned — drop all backing fields in the skeleton.
- `ref struct` cannot implement interfaces, cannot be boxed, and cannot be stored in arrays or class fields. Embrace the constraint.
- `in` parameters for `Dot` communicate "read-only reference" and eliminate defensive copies when combined with `readonly struct`.

## Run

Write your own tests under `practice.tests/Structs/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Structs"
```

## Reference

`solution/Structs/` — see `Vector2.cs` and `SpanReader.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/builtin-types/ref-struct
