namespace Katas.Structs;

/// <summary>
/// A two-dimensional vector with <see cref="double"/> components,
/// implemented as an immutable value type.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why <c>readonly struct</c>?</b>
/// <list type="bullet">
///   <item>
///     <description>
///       <b>No defensive copies:</b>  When a struct is not <c>readonly</c>, the JIT
///       must emit a hidden copy whenever a member is called on a value stored in a
///       <c>readonly</c> field or passed as an <c>in</c> parameter, because the
///       method could theoretically mutate the struct.  Marking the struct
///       <c>readonly</c> eliminates those silent copies, both reducing allocations
///       and preventing hard-to-diagnose mutation bugs.
///     </description>
///   </item>
///   <item>
///     <description>
///       <b>Value semantics:</b>  Vectors have no identity — two vectors with the
///       same components should be considered equal.  A struct encodes that intent
///       directly.
///     </description>
///   </item>
///   <item>
///     <description>
///       <b>Inline storage:</b>  Arrays of <c>Vector2</c> store values contiguously,
///       improving cache locality compared to an array of reference-type objects.
///     </description>
///   </item>
/// </list>
/// </para>
/// <para>
/// <b>Why <c>in</c> parameters for <see cref="Dot"/>?</b>
/// Passing a large struct <c>in</c> tells the compiler to pass a managed pointer
/// (like <c>ref readonly</c>) rather than copying the value on to the stack.
/// For a two-field <c>double</c> struct this makes no measurable difference,
/// but it documents the intent of "read-only reference" and avoids the defensive
/// copy that would be emitted if the struct were not <c>readonly</c>.
/// </para>
/// <para>
/// <b>IEquatable&lt;Vector2&gt;:</b>  Providing a typed <c>Equals</c> avoids boxing
/// when comparing two <c>Vector2</c> values.  The <c>==</c> and <c>!=</c> operators
/// delegate to it, keeping all equality surfaces consistent.
/// </para>
/// <para>
/// <b>GetHashCode:</b>  <see cref="HashCode.Combine"/> produces well-distributed codes
/// and satisfies the contract: if <c>a == b</c> then <c>a.GetHashCode() == b.GetHashCode()</c>.
/// </para>
/// </remarks>
public readonly struct Vector2 : IEquatable<Vector2>
{
    /// <summary>Gets the X (horizontal) component.</summary>
    public double X { get; }

    /// <summary>Gets the Y (vertical) component.</summary>
    public double Y { get; }

    /// <summary>Initialises a new vector with the given components.</summary>
    /// <param name="x">X component.</param>
    /// <param name="y">Y component.</param>
    public Vector2(double x, double y) { X = x; Y = y; }

    // -------------------------------------------------------------------------
    // Arithmetic operators
    // -------------------------------------------------------------------------

    /// <summary>Component-wise addition.</summary>
    public static Vector2 operator +(Vector2 a, Vector2 b) => new(a.X + b.X, a.Y + b.Y);

    /// <summary>Component-wise subtraction.</summary>
    public static Vector2 operator -(Vector2 a, Vector2 b) => new(a.X - b.X, a.Y - b.Y);

    /// <summary>Scales the vector by a scalar multiplier.</summary>
    public static Vector2 operator *(Vector2 v, double scalar) => new(v.X * scalar, v.Y * scalar);

    /// <inheritdoc cref="operator *(Vector2, double)"/>
    public static Vector2 operator *(double scalar, Vector2 v) => v * scalar;

    // -------------------------------------------------------------------------
    // Dot product
    // -------------------------------------------------------------------------

    /// <summary>
    /// Returns the dot product of this vector and <paramref name="other"/>.
    /// </summary>
    /// <param name="other">
    /// The other vector.  Passed <c>in</c> to avoid a copy — see the type-level
    /// remarks for why <c>in</c> and <c>readonly struct</c> work together to
    /// eliminate defensive copies.
    /// </param>
    /// <returns><c>X * other.X + Y * other.Y</c>.</returns>
    public double Dot(in Vector2 other) => X * other.X + Y * other.Y;

    // -------------------------------------------------------------------------
    // Derived properties
    // -------------------------------------------------------------------------

    /// <summary>
    /// Gets the Euclidean length (magnitude) of the vector:
    /// <c>√(X² + Y²)</c>.
    /// </summary>
    public double Length => Math.Sqrt(X * X + Y * Y);

    // -------------------------------------------------------------------------
    // Equality
    // -------------------------------------------------------------------------

    /// <inheritdoc/>
    public bool Equals(Vector2 other) => X == other.X && Y == other.Y;

    /// <inheritdoc/>
    public override bool Equals(object? obj) => obj is Vector2 other && Equals(other);

    /// <inheritdoc/>
    public override int GetHashCode() => HashCode.Combine(X, Y);

    /// <summary>Returns <c>true</c> when all components are equal.</summary>
    public static bool operator ==(Vector2 left, Vector2 right) => left.Equals(right);

    /// <summary>Returns <c>true</c> when any component differs.</summary>
    public static bool operator !=(Vector2 left, Vector2 right) => !left.Equals(right);

    // -------------------------------------------------------------------------
    // ToString
    // -------------------------------------------------------------------------

    /// <summary>Returns a human-readable representation such as <c>"(1, 2)"</c>.</summary>
    public override string ToString() => $"({X}, {Y})";
}
