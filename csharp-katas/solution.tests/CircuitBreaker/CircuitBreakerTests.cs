using Katas.CircuitBreaker;
using Microsoft.Extensions.Time.Testing;

// `CircuitBreaker` resolves to a namespace (nested under Katas), which shadows a same-named
// using-alias, so alias the type under a non-colliding name and use that in the tests.
using Breaker = Katas.CircuitBreaker.CircuitBreaker;

namespace Katas.Tests.CircuitBreaker;

public sealed class CircuitBreakerTests
{
    private static Task<int> Succeed() => Task.FromResult(42);
    private static Task<int> Fail() => Task.FromException<int>(new InvalidOperationException("downstream failure"));

    // ── Opens after failure threshold ─────────────────────────────────────

    [Fact]
    public async Task ExecuteAsync_Should_OpenCircuitAfterThresholdFailures()
    {
        var cb = new Breaker(failureThreshold: 2, openDuration: TimeSpan.FromSeconds(30), halfOpenSuccessesToClose: 1);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Closed, cb.State);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Open, cb.State);
    }

    [Fact]
    public async Task ExecuteAsync_Should_ResetFailureCounterOnSuccess()
    {
        var cb = new Breaker(failureThreshold: 2, openDuration: TimeSpan.FromSeconds(30), halfOpenSuccessesToClose: 1);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        // success resets the counter
        await cb.ExecuteAsync(_ => Succeed());

        // Need 2 new failures to trip again.
        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Closed, cb.State); // still closed after 1 failure

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Open, cb.State);
    }

    // ── Fast-fails when open — action delegate not invoked ────────────────

    [Fact]
    public async Task ExecuteAsync_Should_FastFailWithoutInvokingAction_WhenOpen()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(10), halfOpenSuccessesToClose: 1, fake);

        // Trip the circuit.
        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Open, cb.State);

        int callCount = 0;

        // Should throw CircuitOpenException without ever calling the delegate.
        await Assert.ThrowsAsync<CircuitOpenException>(() =>
            cb.ExecuteAsync(_ =>
            {
                Interlocked.Increment(ref callCount);
                return Succeed();
            }));

        Assert.Equal(0, callCount);
    }

    [Fact]
    public async Task ExecuteAsync_Should_ContinueToFastFailDuringOpenDuration()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(10), halfOpenSuccessesToClose: 1, fake);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));

        // Advance less than the open duration.
        fake.Advance(TimeSpan.FromSeconds(9));

        await Assert.ThrowsAsync<CircuitOpenException>(() => cb.ExecuteAsync(_ => Succeed()));
        Assert.Equal(CircuitState.Open, cb.State);
    }

    // ── HalfOpen: success closes, failure reopens ─────────────────────────

    [Fact]
    public async Task ExecuteAsync_Should_TransitionToHalfOpenAfterOpenDuration()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(5), halfOpenSuccessesToClose: 1, fake);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Open, cb.State);

        fake.Advance(TimeSpan.FromSeconds(5));

        // First call after open duration should go through (HalfOpen).
        int result = await cb.ExecuteAsync(_ => Succeed());
        Assert.Equal(42, result);
        Assert.Equal(CircuitState.Closed, cb.State);
    }

    [Fact]
    public async Task ExecuteAsync_Should_CloseAfterRequiredSuccessesInHalfOpen()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(1), halfOpenSuccessesToClose: 3, fake);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        fake.Advance(TimeSpan.FromSeconds(1));

        // 2 successes — should still be HalfOpen.
        await cb.ExecuteAsync(_ => Succeed());
        await cb.ExecuteAsync(_ => Succeed());
        Assert.Equal(CircuitState.HalfOpen, cb.State);

        // 3rd success closes.
        await cb.ExecuteAsync(_ => Succeed());
        Assert.Equal(CircuitState.Closed, cb.State);
    }

    [Fact]
    public async Task ExecuteAsync_Should_ReopenCircuitOnFailureInHalfOpen()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(1), halfOpenSuccessesToClose: 2, fake);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        fake.Advance(TimeSpan.FromSeconds(1)); // → HalfOpen

        // Failure in HalfOpen reopens.
        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        Assert.Equal(CircuitState.Open, cb.State);
    }

    // ── StateChanged events fire in the correct order ─────────────────────

    [Fact]
    public async Task StateChanged_Should_FireCorrectTransitionSequence()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(1), halfOpenSuccessesToClose: 1, fake);

        var states = new List<CircuitState>();
        cb.StateChanged += (_, s) => states.Add(s);

        // Closed → Open
        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        // Open → HalfOpen
        fake.Advance(TimeSpan.FromSeconds(1));
        // HalfOpen → Closed
        await cb.ExecuteAsync(_ => Succeed());

        Assert.Equal(new[] { CircuitState.Open, CircuitState.HalfOpen, CircuitState.Closed }, states);
    }

    [Fact]
    public async Task StateChanged_Should_FireOpenThenHalfOpenThenOpenOnHalfOpenFailure()
    {
        var fake = new FakeTimeProvider();
        var cb = new Breaker(failureThreshold: 1, openDuration: TimeSpan.FromSeconds(1), halfOpenSuccessesToClose: 1, fake);

        var states = new List<CircuitState>();
        cb.StateChanged += (_, s) => states.Add(s);

        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));
        fake.Advance(TimeSpan.FromSeconds(1));
        await Assert.ThrowsAsync<InvalidOperationException>(() => cb.ExecuteAsync(_ => Fail()));

        Assert.Equal(new[] { CircuitState.Open, CircuitState.HalfOpen, CircuitState.Open }, states);
    }
}
