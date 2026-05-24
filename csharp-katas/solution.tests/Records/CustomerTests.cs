namespace Katas.Tests.Records;

using Katas.Records;

public sealed class CustomerTests
{
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Address MakeAddress(string city = "London") =>
        new("10 Downing St", city, "SW1A 2AA");

    private static Customer MakeCustomer(string name = "Alice", string city = "London") =>
        new(name, MakeAddress(city), new[] { "vip", "newsletter" });

    // -------------------------------------------------------------------------
    // Value equality
    // -------------------------------------------------------------------------

    [Fact]
    public void Equals_Should_ReturnTrue_WhenAllMembersAreStructurallyEqual()
    {
        var a = MakeCustomer();
        var b = MakeCustomer();

        Assert.Equal(a, b);
        Assert.True(a == b);
    }

    [Fact]
    public void Equals_Should_ReturnFalse_WhenNamesAreDifferent()
    {
        var a = MakeCustomer(name: "Alice");
        var b = MakeCustomer(name: "Bob");

        Assert.NotEqual(a, b);
        Assert.True(a != b);
    }

    [Fact]
    public void Equals_Should_ReturnFalse_WhenAddressesAreDifferent()
    {
        var a = MakeCustomer(city: "London");
        var b = MakeCustomer(city: "Paris");

        Assert.NotEqual(a, b);
    }

    [Fact]
    public void GetHashCode_Should_BeConsistentWithEquality()
    {
        var a = MakeCustomer();
        var b = MakeCustomer();

        Assert.Equal(a.GetHashCode(), b.GetHashCode());
    }

    // -------------------------------------------------------------------------
    // Reference vs value semantics
    // -------------------------------------------------------------------------

    [Fact]
    public void ReferenceEquality_Should_BeFalse_ForTwoStructurallyEqualInstances()
    {
        var a = MakeCustomer();
        var b = MakeCustomer();

        // Record equality is value-based; reference identity should differ
        Assert.False(ReferenceEquals(a, b));
        Assert.Equal(a, b); // but value equality holds
    }

    // -------------------------------------------------------------------------
    // With-expressions
    // -------------------------------------------------------------------------

    [Fact]
    public void WithExpression_Should_ProduceNewInstance()
    {
        var original = MakeCustomer();
        var updated = original with { Name = "Bob" };

        Assert.False(ReferenceEquals(original, updated));
    }

    [Fact]
    public void WithExpression_Should_LeaveOriginalUnchanged()
    {
        var original = MakeCustomer(name: "Alice");
        _ = original with { Name = "Bob" };

        Assert.Equal("Alice", original.Name);
    }

    [Fact]
    public void WithExpression_Should_CopyUnchangedMembers()
    {
        var original = MakeCustomer();
        var updated = original with { Name = "Bob" };

        Assert.Equal(original.Address, updated.Address);
        Assert.Equal(original.Tags, updated.Tags);
    }

    // -------------------------------------------------------------------------
    // Deconstruction
    // -------------------------------------------------------------------------

    [Fact]
    public void Deconstruct_Should_BindAllPositionalMembers()
    {
        var customer = MakeCustomer(name: "Alice", city: "London");

        var (name, address, tags) = customer;

        Assert.Equal("Alice", name);
        Assert.Equal("London", address.City);
        Assert.Equal(2, tags.Count);
    }

    // -------------------------------------------------------------------------
    // ToString
    // -------------------------------------------------------------------------

    [Fact]
    public void ToString_Should_IncludeAllMembers()
    {
        var customer = MakeCustomer(name: "Alice");
        var text = customer.ToString();

        Assert.Contains("Alice", text);
        Assert.Contains("London", text);
    }

    // -------------------------------------------------------------------------
    // Invariant validation
    // -------------------------------------------------------------------------

    [Fact]
    public void Constructor_Should_Throw_WhenNameIsEmpty()
    {
        Assert.Throws<ArgumentException>(() => new Customer("", MakeAddress(), Array.Empty<string>()));
    }

    [Fact]
    public void Constructor_Should_Throw_WhenNameIsWhitespace()
    {
        Assert.Throws<ArgumentException>(() => new Customer("   ", MakeAddress(), Array.Empty<string>()));
    }

    // -------------------------------------------------------------------------
    // Computed member
    // -------------------------------------------------------------------------

    [Fact]
    public void TagCount_Should_ReflectNumberOfTags()
    {
        var customer = new Customer("Alice", MakeAddress(), new[] { "a", "b", "c" });
        Assert.Equal(3, customer.TagCount);
    }

    // -------------------------------------------------------------------------
    // CustomerOperations
    // -------------------------------------------------------------------------

    [Fact]
    public void MoveCity_Should_ReturnCustomerWithUpdatedCity()
    {
        var original = MakeCustomer(city: "London");
        var moved = CustomerOperations.MoveCity(original, "Paris");

        Assert.Equal("Paris", moved.Address.City);
    }

    [Fact]
    public void MoveCity_Should_LeaveOriginalCityUnchanged()
    {
        var original = MakeCustomer(city: "London");
        _ = CustomerOperations.MoveCity(original, "Paris");

        Assert.Equal("London", original.Address.City);
    }

    [Fact]
    public void MoveCity_Should_PreserveOtherAddressFields()
    {
        var original = MakeCustomer(city: "London");
        var moved = CustomerOperations.MoveCity(original, "Paris");

        Assert.Equal(original.Address.Street, moved.Address.Street);
        Assert.Equal(original.Address.PostCode, moved.Address.PostCode);
    }

    [Fact]
    public void AddTag_Should_AppendTagAndLeaveOriginalUnchanged()
    {
        var original = MakeCustomer();
        var updated = CustomerOperations.AddTag(original, "new-tag");

        Assert.Contains("new-tag", updated.Tags);
        Assert.DoesNotContain("new-tag", original.Tags);
    }
}
