namespace Katas.Records;

/// <summary>
/// Represents a customer as an immutable value object, combining a name, an address,
/// and a read-only list of tags.
/// </summary>
/// <param name="Name">Non-empty display name for the customer.</param>
/// <param name="Address">Current postal address.</param>
/// <param name="Tags">Zero-or-more classification tags.</param>
/// <remarks>
/// <para>
/// <b>Invariant via compact constructor:</b>  The compact (primary) constructor body runs
/// before the compiler-generated property assignments, making it the right place to
/// validate invariants.  Here we ensure <c>Name</c> is not blank.
/// If validation is needed after assignment (e.g. cross-property checks), place it
/// in the body after the positional assignments.
/// </para>
/// <para>
/// <b>Computed member <c>TagCount</c>:</b>  A get-only property can be added to a
/// positional record without affecting value equality or the generated constructor —
/// computed members are excluded from the synthesised <c>Equals</c> and
/// <c>GetHashCode</c> because they are not init-only positional properties.
/// </para>
/// <para>
/// <b>IReadOnlyList vs ImmutableList:</b>  <c>IReadOnlyList&lt;string&gt;</c> prevents
/// mutation through the interface but does not prevent the caller from casting to
/// <c>List&lt;T&gt;</c> and mutating the underlying collection.  For airtight immutability,
/// accept <c>IEnumerable&lt;string&gt;</c> in the ctor and store
/// <c>tags.ToList().AsReadOnly()</c> or use <c>ImmutableArray&lt;string&gt;</c>.
/// We keep <c>IReadOnlyList&lt;string&gt;</c> here to stay BCL-only and focus on
/// record semantics rather than collection defence.
/// </para>
/// <para>
/// <b>With-expression depth:</b>  A nested with-expression such as
/// <c>c with { Address = c.Address with { City = newCity } }</c> cleanly models
/// hierarchical immutable updates.  See <see cref="CustomerOperations.MoveCity"/>.
/// </para>
/// <para>
/// <b>ToString:</b>  The compiler generates a human-readable
/// <c>Customer { Name = …, Address = …, Tags = … }</c> representation — ideal for
/// diagnostics and test failure messages.
/// </para>
/// </remarks>
public record Customer(string Name, Address Address, IReadOnlyList<string> Tags)
{
    // Compact constructor body — runs before positional property assignments are stored.
    // Use this for invariant checks that reference the incoming parameter values.
    /// <summary>Gets the display name. Validated non-empty at construction time.</summary>
    public string Name { get; init; } = string.IsNullOrWhiteSpace(Name)
        ? throw new ArgumentException("Customer name must not be empty.", nameof(Name))
        : Name;

    /// <summary>
    /// Gets the number of tags associated with the customer.
    /// </summary>
    /// <remarks>
    /// Computed members like this are deliberately excluded from the auto-generated
    /// <c>Equals</c> / <c>GetHashCode</c> / <c>ToString</c> implementations because they
    /// are not positional init-only properties.  This is consistent behaviour:
    /// two customers with the same Name/Address/Tags are equal regardless of any
    /// derived property.
    /// </remarks>
    public int TagCount => Tags.Count;

    /// <summary>
    /// Records compare reference-typed members with the default equality comparer, which for
    /// <see cref="IReadOnlyList{T}"/> means <b>reference</b> equality — two customers with
    /// equal-but-distinct tag lists would otherwise be considered unequal. This is a common
    /// records pitfall: the synthesised equality is shallow for collection members. We override
    /// <see cref="Equals(Customer)"/> and <see cref="GetHashCode"/> to compare <see cref="Tags"/>
    /// structurally. (The compiler still synthesises <c>==</c>, <c>!=</c>, <c>Equals(object)</c>,
    /// the copy constructor, and <c>EqualityContract</c> for us.)
    /// </summary>
    public virtual bool Equals(Customer? other) =>
        other is not null
        && EqualityContract == other.EqualityContract
        && Name == other.Name
        && Address == other.Address
        && Tags.SequenceEqual(other.Tags);

    /// <inheritdoc/>
    public override int GetHashCode()
    {
        var hash = new HashCode();
        hash.Add(Name);
        hash.Add(Address);
        foreach (var tag in Tags)
            hash.Add(tag);
        return hash.ToHashCode();
    }
}
