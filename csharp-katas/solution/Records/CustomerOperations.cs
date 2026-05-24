namespace Katas.Records;

/// <summary>
/// Pure, stateless operations on <see cref="Customer"/> instances.
/// All methods return new records; no input is ever mutated.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why a static class?</b>  These are free functions — they have no state of their
/// own and do not belong to any particular domain service.  A static class documents
/// that intent and avoids the overhead of instantiation.
/// </para>
/// <para>
/// <b>Nested with-expressions:</b>  Records support with-expressions at every level of
/// a nested hierarchy.  The pattern
/// <code>
/// outer with { Inner = outer.Inner with { Prop = newValue } }
/// </code>
/// reads almost like a declarative diff: "give me a copy of <c>outer</c> where its
/// <c>Inner</c> is a copy of the current <c>Inner</c> with <c>Prop</c> changed to
/// <c>newValue</c>".
/// </para>
/// <para>
/// <b>Referential transparency:</b>  Because records are value objects, these functions
/// are referentially transparent — the same inputs always produce the same output, and
/// no observable side-effects occur.  That makes them trivially unit-testable and safe
/// to compose in any order.
/// </para>
/// </remarks>
public static class CustomerOperations
{
    /// <summary>
    /// Returns a new <see cref="Customer"/> identical to <paramref name="customer"/>
    /// except that the city component of their address is replaced with
    /// <paramref name="newCity"/>.
    /// </summary>
    /// <param name="customer">The source customer. Must not be <c>null</c>.</param>
    /// <param name="newCity">The replacement city name. Must not be <c>null</c> or whitespace.</param>
    /// <returns>A new <see cref="Customer"/> with the updated city. The original is unchanged.</returns>
    /// <exception cref="ArgumentNullException"><paramref name="customer"/> is <c>null</c>.</exception>
    /// <exception cref="ArgumentException"><paramref name="newCity"/> is null, empty, or whitespace.</exception>
    /// <remarks>
    /// Demonstrates a two-level nested with-expression.  The outer <c>with</c> copies
    /// the <c>Customer</c> record; the inner <c>with</c> copies the <c>Address</c> record,
    /// changing only <c>City</c>.  Both the customer object and the original address object
    /// are left intact, which is essential when the same address or customer is shared
    /// across multiple data structures.
    /// </remarks>
    public static Customer MoveCity(Customer customer, string newCity)
    {
        if (customer is null) throw new ArgumentNullException(nameof(customer));
        if (string.IsNullOrWhiteSpace(newCity))
            throw new ArgumentException("City must not be empty.", nameof(newCity));

        return customer with
        {
            Address = customer.Address with { City = newCity }
        };
    }

    /// <summary>
    /// Returns a new <see cref="Customer"/> with the given tag appended to the existing tag list.
    /// </summary>
    /// <param name="customer">Source customer.</param>
    /// <param name="tag">Tag to add.</param>
    /// <returns>A new customer whose <c>Tags</c> includes <paramref name="tag"/> at the end.</returns>
    /// <remarks>
    /// Because <c>IReadOnlyList&lt;string&gt;</c> is an interface, we must materialise
    /// the new list before storing it in the with-expression.  We use
    /// <c>new List&lt;string&gt;</c> and return it as the interface type — the record
    /// stores the reference, not a copy, so callers should not mutate the list after
    /// handing it to this method.
    /// </remarks>
    public static Customer AddTag(Customer customer, string tag)
    {
        if (customer is null) throw new ArgumentNullException(nameof(customer));
        if (string.IsNullOrWhiteSpace(tag))
            throw new ArgumentException("Tag must not be empty.", nameof(tag));

        var newTags = new List<string>(customer.Tags) { tag };
        return customer with { Tags = newTags };
    }
}
