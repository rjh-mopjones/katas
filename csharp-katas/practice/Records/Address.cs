namespace Katas.Records;

/// <summary>
/// Represents a postal address as an immutable value object.
/// </summary>
/// <param name="Street">Street name and number.</param>
/// <param name="City">City or town.</param>
/// <param name="PostCode">Postal / ZIP code.</param>
public record Address(string Street, string City, string PostCode);
