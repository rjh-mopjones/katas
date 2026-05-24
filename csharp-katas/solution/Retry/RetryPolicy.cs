namespace Katas.Retry;

/// <summary>
/// Immutable configuration that drives exponential-backoff retry behaviour.
/// </summary>
/// <param name="MaxAttempts">
/// Total number of attempts (including the first try). Must be at least 1.
/// </param>
/// <param name="BaseDelay">
/// Delay before the second attempt. Must be non-negative.
/// Subsequent delays are multiplied by <see cref="Multiplier"/> each attempt.
/// </param>
/// <param name="MaxDelay">
/// Upper bound on any single inter-attempt delay. Must be &gt;= <see cref="BaseDelay"/>.
/// Prevents the exponential from growing unboundedly.
/// </param>
/// <param name="Multiplier">
/// Growth factor applied to <see cref="BaseDelay"/> each successive attempt. Must be &gt;= 1.0.
/// A value of 1.0 gives constant-backoff; 2.0 doubles the delay each attempt.
/// </param>
/// <param name="UseJitter">
/// When <see langword="true"/>, full-jitter is applied: the computed delay is scaled by a
/// random fraction in <c>[0, 1)</c>. Full jitter is preferred over additive jitter in
/// distributed systems because it de-synchronises retries across many concurrent clients,
/// dramatically reducing thundering-herd spikes on a recovering downstream service.
/// See AWS Architecture Blog "Exponential Backoff And Jitter" (2015).
/// </param>
/// <remarks>
/// <b>Pure computation:</b> <see cref="ComputeDelay"/> is a pure function — it takes an
/// explicit <paramref name="jitterFraction"/> so tests can pass a fixed value (e.g. 0)
/// for fully deterministic assertions, while production code passes
/// <c>Random.Shared.NextDouble()</c>.
/// </remarks>
public sealed record RetryPolicy(
    int MaxAttempts,
    TimeSpan BaseDelay,
    TimeSpan MaxDelay,
    double Multiplier,
    bool UseJitter)
{
    // C# records have no Kotlin/Java-style "compact constructor". The idiomatic way to validate a
    // positional record is to redeclare each property with an initialiser that references the
    // same-named primary-constructor parameter (in scope here) and throws on invalid input. These
    // initialisers run as part of the synthesised primary constructor.
    public int MaxAttempts { get; init; } = MaxAttempts >= 1
        ? MaxAttempts
        : throw new ArgumentOutOfRangeException(nameof(MaxAttempts), "MaxAttempts must be at least 1.");

    public TimeSpan BaseDelay { get; init; } = BaseDelay >= TimeSpan.Zero
        ? BaseDelay
        : throw new ArgumentOutOfRangeException(nameof(BaseDelay), "BaseDelay must be non-negative.");

    public TimeSpan MaxDelay { get; init; } = MaxDelay >= BaseDelay
        ? MaxDelay
        : throw new ArgumentOutOfRangeException(nameof(MaxDelay), "MaxDelay must be >= BaseDelay.");

    public double Multiplier { get; init; } = Multiplier >= 1.0
        ? Multiplier
        : throw new ArgumentOutOfRangeException(nameof(Multiplier), "Multiplier must be >= 1.0.");

    /// <summary>
    /// Computes the delay before attempt number <paramref name="attempt"/>.
    /// </summary>
    /// <param name="attempt">
    /// 1-based attempt index. The delay before attempt 1 (the first retry, i.e. after the
    /// initial failure) uses <c>attempt = 1</c>: <c>BaseDelay * Multiplier^0 = BaseDelay</c>.
    /// </param>
    /// <param name="jitterFraction">
    /// A value in <c>[0, 1)</c> applied as a full-jitter multiplier when
    /// <see cref="UseJitter"/> is <see langword="true"/>. Pass 0 for deterministic tests.
    /// </param>
    /// <returns>The clamped, optionally-jittered delay.</returns>
    /// <remarks>
    /// <b>Overflow safety:</b> Very large exponents can overflow <see cref="double"/>. The
    /// intermediate computation is done in <c>double</c> ticks and then clamped to
    /// <see cref="MaxDelay"/> before conversion back to <see cref="TimeSpan"/>, which
    /// prevents <see cref="OverflowException"/> from <c>TimeSpan.FromTicks</c>.
    /// </remarks>
    public TimeSpan ComputeDelay(int attempt, double jitterFraction)
    {
        // Exponent is zero-based: attempt 1 → exponent 0 (= BaseDelay * Multiplier^0).
        double exponent = attempt - 1;
        double rawTicks = BaseDelay.Ticks * Math.Pow(Multiplier, exponent);

        // Clamp before converting to avoid TimeSpan.FromTicks overflow.
        double clampedTicks = Math.Min(rawTicks, MaxDelay.Ticks);
        TimeSpan delay = TimeSpan.FromTicks((long)clampedTicks);

        if (UseJitter)
            delay = TimeSpan.FromTicks((long)(delay.Ticks * jitterFraction));

        return delay;
    }
}
