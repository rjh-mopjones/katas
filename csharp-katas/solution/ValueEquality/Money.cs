namespace Katas.ValueEquality;

/// <summary>
/// Represents a monetary amount in a specific currency as an immutable value type.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why <c>readonly struct</c>?</b>  A monetary value has no identity — two instances
/// representing "10 USD" should be indistinguishable.  A struct avoids heap allocation
/// and GC pressure for short-lived values (e.g. arithmetic intermediates in a pricing
/// loop).  The <c>readonly</c> modifier guarantees that no method or property can mutate
/// the fields, which prevents defensive copies when the struct is passed as an <c>in</c>
/// parameter or stored in a read-only context.
/// </para>
/// <para>
/// <b>IEquatable&lt;Money&gt; contract:</b>  Implementing <see cref="IEquatable{T}"/>
/// provides a strongly-typed <c>Equals(Money)</c> that avoids boxing.  The <c>object.Equals</c>
/// override delegates to it for consistency.  The <c>==</c> and <c>!=</c> operators also
/// delegate, so all three equality surfaces agree.
/// </para>
/// <para>
/// <b>GetHashCode consistency:</b>  If <c>a.Equals(b)</c> is <c>true</c>, then
/// <c>a.GetHashCode() == b.GetHashCode()</c> must also be <c>true</c>.  Here we combine
/// <c>Amount</c> and a normalised (upper-case) <c>Currency</c> so that "usd" and "USD"
/// hash identically and compare as equal.  The currency is normalised in the constructor
/// to avoid repeated string allocations at comparison time.
/// </para>
/// <para>
/// <b>Currency-mismatch exceptions:</b>  Addition and subtraction across different
/// currencies are nonsensical (you cannot add USD and EUR without an exchange rate).
/// We throw <see cref="InvalidOperationException"/> rather than returning a result or
/// silently ignoring the mismatch.  Multiplication by a scalar is dimensionally sound
/// (USD * 1.5 = USD), so no currency check is needed there.
/// </para>
/// <para>
/// <b>IComparable&lt;Money&gt;:</b>  Ordering is defined within the same currency.
/// Cross-currency comparison throws <see cref="InvalidOperationException"/> for the same
/// reason as arithmetic.  The relational operators (<c>&lt;</c>, <c>&gt;</c>, etc.)
/// delegate to <see cref="CompareTo(Money)"/>.
/// </para>
/// <para>
/// <b>Alternative — class-based Money:</b>  A reference type adds GC pressure but
/// allows richer behaviour (e.g. polymorphism, null-value sentinels).  For DDD value
/// objects that rarely appear in hot loops, a class is often simpler.
/// </para>
/// </remarks>
public readonly struct Money : IEquatable<Money>, IComparable<Money>
{
    /// <summary>Gets the numeric amount.</summary>
    public decimal Amount { get; }

    /// <summary>
    /// Gets the ISO 4217 currency code, normalised to upper-case
    /// (e.g. "USD", "EUR", "GBP").
    /// </summary>
    public string Currency { get; }

    /// <summary>
    /// Initialises a new <see cref="Money"/> instance.
    /// </summary>
    /// <param name="amount">The monetary amount.</param>
    /// <param name="currency">
    /// ISO 4217 currency code.  Stored upper-case so "usd" and "USD" are treated
    /// identically throughout the type's equality and arithmetic logic.
    /// </param>
    /// <exception cref="ArgumentException">
    /// <paramref name="currency"/> is null, empty, or whitespace.
    /// </exception>
    public Money(decimal amount, string currency)
    {
        if (string.IsNullOrWhiteSpace(currency))
            throw new ArgumentException("Currency code must not be empty.", nameof(currency));

        Amount = amount;
        Currency = currency.ToUpperInvariant();
    }

    /// <summary>
    /// Returns a zero-amount <see cref="Money"/> in the given currency.
    /// Useful as an accumulator seed (e.g. in a fold / LINQ <c>Aggregate</c>).
    /// </summary>
    /// <param name="currency">Currency code.</param>
    /// <returns>A <see cref="Money"/> with <c>Amount == 0</c>.</returns>
    public static Money Zero(string currency) => new(0m, currency);

    // -------------------------------------------------------------------------
    // Arithmetic operators
    // -------------------------------------------------------------------------

    /// <summary>
    /// Adds two monetary values of the same currency.
    /// </summary>
    /// <exception cref="InvalidOperationException">
    /// The currencies differ.  You cannot add monetary values of different currencies
    /// without an explicit exchange rate.
    /// </exception>
    public static Money operator +(Money left, Money right)
    {
        EnsureSameCurrency(left, right, "+");
        return new Money(left.Amount + right.Amount, left.Currency);
    }

    /// <summary>
    /// Subtracts two monetary values of the same currency.
    /// </summary>
    /// <exception cref="InvalidOperationException">The currencies differ.</exception>
    public static Money operator -(Money left, Money right)
    {
        EnsureSameCurrency(left, right, "-");
        return new Money(left.Amount - right.Amount, left.Currency);
    }

    /// <summary>
    /// Scales a monetary value by a dimensionless scalar.
    /// The currency is preserved: USD * 1.5 = 1.5 USD.
    /// </summary>
    /// <param name="money">The monetary value.</param>
    /// <param name="scalar">Multiplier (may be fractional or negative).</param>
    public static Money operator *(Money money, decimal scalar) =>
        new(money.Amount * scalar, money.Currency);

    /// <inheritdoc cref="operator *(Money, decimal)"/>
    public static Money operator *(decimal scalar, Money money) => money * scalar;

    // -------------------------------------------------------------------------
    // Comparison operators
    // -------------------------------------------------------------------------

    /// <inheritdoc cref="CompareTo(Money)"/>
    public static bool operator ==(Money left, Money right) => left.Equals(right);

    /// <inheritdoc cref="CompareTo(Money)"/>
    public static bool operator !=(Money left, Money right) => !left.Equals(right);

    /// <exception cref="InvalidOperationException">The currencies differ.</exception>
    public static bool operator <(Money left, Money right)  => left.CompareTo(right) < 0;
    /// <exception cref="InvalidOperationException">The currencies differ.</exception>
    public static bool operator >(Money left, Money right)  => left.CompareTo(right) > 0;
    /// <exception cref="InvalidOperationException">The currencies differ.</exception>
    public static bool operator <=(Money left, Money right) => left.CompareTo(right) <= 0;
    /// <exception cref="InvalidOperationException">The currencies differ.</exception>
    public static bool operator >=(Money left, Money right) => left.CompareTo(right) >= 0;

    // -------------------------------------------------------------------------
    // IComparable<Money>
    // -------------------------------------------------------------------------

    /// <summary>
    /// Compares this instance to <paramref name="other"/> within the same currency.
    /// </summary>
    /// <param name="other">Value to compare.</param>
    /// <returns>
    /// Negative if this is less than <paramref name="other"/>, zero if equal,
    /// positive if greater.
    /// </returns>
    /// <exception cref="InvalidOperationException">
    /// <paramref name="other"/> uses a different currency.
    /// Cross-currency ordering is undefined without an exchange rate.
    /// </exception>
    public int CompareTo(Money other)
    {
        EnsureSameCurrency(this, other, "CompareTo");
        return Amount.CompareTo(other.Amount);
    }

    // -------------------------------------------------------------------------
    // IEquatable<Money> + object overrides
    // -------------------------------------------------------------------------

    /// <summary>
    /// Returns <c>true</c> when both <c>Amount</c> and <c>Currency</c> are equal.
    /// Currency comparison is case-insensitive because the constructor normalises
    /// to upper-case, so "usd" and "USD" are treated as the same currency.
    /// </summary>
    public bool Equals(Money other) =>
        Amount == other.Amount &&
        string.Equals(Currency, other.Currency, StringComparison.OrdinalIgnoreCase);

    /// <inheritdoc/>
    public override bool Equals(object? obj) => obj is Money other && Equals(other);

    /// <summary>
    /// Returns a hash code consistent with <see cref="Equals(Money)"/>.
    /// </summary>
    /// <remarks>
    /// <para>
    /// Both <c>Amount</c> and the upper-case <c>Currency</c> are folded into the hash.
    /// Using <see cref="HashCode.Combine"/> avoids manual XOR arithmetic and produces
    /// well-distributed codes.  Because the constructor already stores <c>Currency</c>
    /// in upper-case, <c>GetHashCode</c> does not need to normalise again.
    /// </para>
    /// <para>
    /// <b>Consistency requirement:</b>  If <c>a == b</c> (i.e. <c>a.Equals(b)</c>),
    /// then <c>a.GetHashCode() == b.GetHashCode()</c> must hold.  The converse need not
    /// be true — hash collisions are allowed.
    /// </para>
    /// </remarks>
    public override int GetHashCode() => HashCode.Combine(Amount, Currency);

    // -------------------------------------------------------------------------
    // ToString
    // -------------------------------------------------------------------------

    /// <summary>
    /// Returns a human-readable representation such as <c>"10.50 USD"</c>.
    /// </summary>
    public override string ToString() => $"{Amount:G} {Currency}";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void EnsureSameCurrency(Money left, Money right, string operation)
    {
        if (!string.Equals(left.Currency, right.Currency, StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException(
                $"Cannot apply '{operation}' to different currencies: {left.Currency} and {right.Currency}.");
    }
}
