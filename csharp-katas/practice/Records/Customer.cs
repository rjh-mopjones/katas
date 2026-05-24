namespace Katas.Records;

/// <summary>
/// Represents a customer as an immutable value object, combining a name, an address,
/// and a read-only list of tags.
/// </summary>
/// <param name="Name">Non-empty display name for the customer.</param>
/// <param name="Address">Current postal address.</param>
/// <param name="Tags">Zero-or-more classification tags.</param>
public record Customer(string Name, Address Address, IReadOnlyList<string> Tags)
{
    /// <summary>Gets the number of tags associated with the customer.</summary>
    public int TagCount => throw new NotImplementedException();

    /// <summary>Compares structurally, including sequence-equal Tags.</summary>
    public virtual bool Equals(Customer? other) => throw new NotImplementedException();

    /// <inheritdoc/>
    public override int GetHashCode() => throw new NotImplementedException();
}
