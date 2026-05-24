namespace Katas.Records;

/// <summary>
/// Pure, stateless operations on <see cref="Customer"/> instances.
/// All methods return new records; no input is ever mutated.
/// </summary>
public static class CustomerOperations
{
    /// <summary>
    /// Returns a new <see cref="Customer"/> with the city component of their address replaced.
    /// </summary>
    public static Customer MoveCity(Customer customer, string newCity) => throw new NotImplementedException();

    /// <summary>
    /// Returns a new <see cref="Customer"/> with the given tag appended to the existing tag list.
    /// </summary>
    public static Customer AddTag(Customer customer, string tag) => throw new NotImplementedException();
}
