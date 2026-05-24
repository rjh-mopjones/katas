namespace Katas.Structs;

/// <summary>
/// A two-dimensional vector with <see cref="double"/> components,
/// implemented as an immutable value type.
/// </summary>
public readonly struct Vector2 : IEquatable<Vector2>
{
    /// <summary>Gets the X (horizontal) component.</summary>
    public double X => throw new NotImplementedException();

    /// <summary>Gets the Y (vertical) component.</summary>
    public double Y => throw new NotImplementedException();

    /// <summary>Initialises a new vector with the given components.</summary>
    public Vector2(double x, double y) { throw new NotImplementedException(); }

    /// <summary>Component-wise addition.</summary>
    public static Vector2 operator +(Vector2 a, Vector2 b) => throw new NotImplementedException();

    /// <summary>Component-wise subtraction.</summary>
    public static Vector2 operator -(Vector2 a, Vector2 b) => throw new NotImplementedException();

    /// <summary>Scales the vector by a scalar multiplier.</summary>
    public static Vector2 operator *(Vector2 v, double scalar) => throw new NotImplementedException();

    /// <summary>Scales the vector by a scalar multiplier.</summary>
    public static Vector2 operator *(double scalar, Vector2 v) => throw new NotImplementedException();

    /// <summary>Returns the dot product of this vector and <paramref name="other"/>.</summary>
    public double Dot(in Vector2 other) => throw new NotImplementedException();

    /// <summary>Gets the Euclidean length (magnitude): sqrt(X^2 + Y^2).</summary>
    public double Length => throw new NotImplementedException();

    /// <inheritdoc/>
    public bool Equals(Vector2 other) => throw new NotImplementedException();

    /// <inheritdoc/>
    public override bool Equals(object? obj) => throw new NotImplementedException();

    /// <inheritdoc/>
    public override int GetHashCode() => throw new NotImplementedException();

    /// <summary>Returns true when all components are equal.</summary>
    public static bool operator ==(Vector2 left, Vector2 right) => throw new NotImplementedException();

    /// <summary>Returns true when any component differs.</summary>
    public static bool operator !=(Vector2 left, Vector2 right) => throw new NotImplementedException();

    /// <summary>Returns a human-readable representation such as "(1, 2)".</summary>
    public override string ToString() => throw new NotImplementedException();
}
