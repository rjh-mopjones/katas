namespace Katas.ValueEquality;

/// <summary>
/// Represents a monetary amount in a specific currency as an immutable value type.
/// </summary>
public readonly struct Money : IEquatable<Money>, IComparable<Money>
{
    /// <summary>Gets the numeric amount.</summary>
    public decimal Amount => throw new NotImplementedException();

    /// <summary>Gets the ISO 4217 currency code, normalised to upper-case.</summary>
    public string Currency => throw new NotImplementedException();

    /// <summary>Initialises a new <see cref="Money"/> instance.</summary>
    public Money(decimal amount, string currency) { throw new NotImplementedException(); }

    /// <summary>Returns a zero-amount <see cref="Money"/> in the given currency.</summary>
    public static Money Zero(string currency) => throw new NotImplementedException();

    /// <summary>Adds two monetary values of the same currency.</summary>
    public static Money operator +(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Subtracts two monetary values of the same currency.</summary>
    public static Money operator -(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Scales a monetary value by a dimensionless scalar.</summary>
    public static Money operator *(Money money, decimal scalar) => throw new NotImplementedException();

    /// <summary>Scales a monetary value by a dimensionless scalar.</summary>
    public static Money operator *(decimal scalar, Money money) => throw new NotImplementedException();

    /// <summary>Returns true when both Amount and Currency are equal.</summary>
    public static bool operator ==(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Returns true when Amount or Currency differ.</summary>
    public static bool operator !=(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Less-than comparison within the same currency.</summary>
    public static bool operator <(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Greater-than comparison within the same currency.</summary>
    public static bool operator >(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Less-than-or-equal comparison within the same currency.</summary>
    public static bool operator <=(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Greater-than-or-equal comparison within the same currency.</summary>
    public static bool operator >=(Money left, Money right) => throw new NotImplementedException();

    /// <summary>Compares this instance to <paramref name="other"/> within the same currency.</summary>
    public int CompareTo(Money other) => throw new NotImplementedException();

    /// <summary>Returns true when both Amount and Currency are equal.</summary>
    public bool Equals(Money other) => throw new NotImplementedException();

    /// <inheritdoc/>
    public override bool Equals(object? obj) => throw new NotImplementedException();

    /// <summary>Returns a hash code consistent with Equals.</summary>
    public override int GetHashCode() => throw new NotImplementedException();

    /// <summary>Returns a human-readable representation such as "10.50 USD".</summary>
    public override string ToString() => throw new NotImplementedException();
}
