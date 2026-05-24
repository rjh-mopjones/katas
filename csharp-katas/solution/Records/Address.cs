namespace Katas.Records;

/// <summary>
/// Represents a postal address as an immutable value object.
/// </summary>
/// <param name="Street">Street name and number.</param>
/// <param name="City">City or town.</param>
/// <param name="PostCode">Postal / ZIP code.</param>
/// <remarks>
/// <para>
/// <b>Why a record?</b>  A record gives us structural value equality for free:
/// two <c>Address</c> instances are equal when all their positional properties match.
/// This is exactly what we want for a value object — compare by content, not by
/// reference identity.
/// </para>
/// <para>
/// <b>Immutability:</b>  Positional record properties are <c>init</c>-only by default.
/// Combined with the immutable nature of <c>string</c>, every <c>Address</c> is
/// effectively frozen after construction.
/// </para>
/// <para>
/// <b>With-expressions:</b>  <c>with { City = "London" }</c> produces a shallow copy
/// with only the specified member changed.  The original instance is untouched —
/// a key property for safe sharing across threads or data structures.
/// </para>
/// <para>
/// <b>Deconstruction:</b>  The compiler synthesises a <c>Deconstruct</c> method
/// matching the positional parameters, allowing <c>var (street, city, post) = address</c>
/// and positional patterns in <c>switch</c> expressions.
/// </para>
/// <para>
/// <b>GetHashCode consistency:</b>  The compiler-generated <c>GetHashCode</c> aggregates
/// all positional properties, so two equal <c>Address</c> values will have identical hash
/// codes — a requirement of the <see cref="object.GetHashCode"/> contract.
/// </para>
/// <para>
/// <b>Alternative — struct record:</b>  For hot paths where heap pressure matters,
/// <c>readonly record struct</c> would store the value inline and eliminate the GC
/// reference.  The trade-off is value-type copy semantics and no <c>null</c> reference.
/// </para>
/// </remarks>
public record Address(string Street, string City, string PostCode);
