namespace Katas.CircuitBreaker;

/// <summary>
/// A classic circuit-breaker that protects a dependency by failing fast when it is
/// repeatedly unavailable, then cautiously probing for recovery.
/// </summary>
/// <remarks>
/// <para>
/// <b>State machine:</b>
/// <list type="bullet">
///   <item><description>
///     <b>Closed</b>: every <see cref="ExecuteAsync{T}"/> call is forwarded to the action.
///     Each exception increments a consecutive-failure counter.  When that counter reaches
///     <c>failureThreshold</c> the circuit trips to <b>Open</b>.
///     A success resets the failure counter to zero.
///   </description></item>
///   <item><description>
///     <b>Open</b>: all calls throw <see cref="CircuitOpenException"/> immediately without
///     invoking the action delegate.  After <c>openDuration</c> has elapsed the circuit
///     transitions to <b>HalfOpen</b> to probe recovery.
///   </description></item>
///   <item><description>
///     <b>HalfOpen</b>: calls are forwarded to the action.  A failure immediately reopens the
///     circuit.  After <c>halfOpenSuccessesToClose</c> consecutive successes the circuit
///     closes.
///   </description></item>
/// </list>
/// </para>
/// <para>
/// <b>Thread-safety:</b>  A <c>lock</c> guards all state reads/writes and transitions.  The
/// lock is <em>not</em> held while awaiting the action delegate — holding a lock across an
/// <c>await</c> is a deadlock risk and would serialise all concurrent callers.  Instead, the
/// lock is acquired to read state before the call and again to record the outcome after it.
/// The price is that in <b>HalfOpen</b> multiple concurrent callers may simultaneously
/// execute probes; for simplicity this is acceptable here (a stricter implementation would
/// allow at most one concurrent probe).
/// </para>
/// <para>
/// <b>TimeProvider:</b>  The open-duration timer uses <see cref="TimeProvider.GetTimestamp"/>
/// so tests can use <c>FakeTimeProvider.Advance</c> rather than real sleeps.
/// </para>
/// <para>
/// <b>StateChanged event:</b>  Fired after each transition, outside the lock, to avoid
/// re-entrancy issues with subscribers that themselves call back into the breaker.
/// </para>
/// </remarks>
public sealed class CircuitBreaker
{
    private readonly int _failureThreshold;
    private readonly TimeSpan _openDuration;
    private readonly int _halfOpenSuccessesToClose;
    private readonly TimeProvider _timeProvider;
    private readonly object _lock = new();

    private CircuitState _state = CircuitState.Closed;
    private int _consecutiveFailures;
    private int _halfOpenSuccesses;
    private long _openedAtTimestamp;

    /// <summary>Raised after each state transition, outside the lock.</summary>
    public event EventHandler<CircuitState>? StateChanged;

    /// <summary>Gets the current circuit state.</summary>
    public CircuitState State
    {
        get { lock (_lock) { return _state; } }
    }

    /// <summary>
    /// Initialises a new circuit breaker.
    /// </summary>
    /// <param name="failureThreshold">
    /// Number of consecutive failures required to trip the circuit from Closed to Open.
    /// Must be ≥ 1.
    /// </param>
    /// <param name="openDuration">
    /// How long the circuit stays Open before transitioning to HalfOpen.
    /// Must be positive.
    /// </param>
    /// <param name="halfOpenSuccessesToClose">
    /// Number of consecutive successes in HalfOpen required to close the circuit.
    /// Must be ≥ 1.
    /// </param>
    /// <param name="timeProvider">
    /// Clock abstraction. Defaults to <see cref="TimeProvider.System"/>.
    /// </param>
    /// <exception cref="ArgumentOutOfRangeException">
    /// Thrown when any numeric argument is out of range.
    /// </exception>
    public CircuitBreaker(
        int failureThreshold,
        TimeSpan openDuration,
        int halfOpenSuccessesToClose,
        TimeProvider? timeProvider = null)
    {
        if (failureThreshold < 1)
            throw new ArgumentOutOfRangeException(nameof(failureThreshold), "Must be at least 1.");
        if (openDuration <= TimeSpan.Zero)
            throw new ArgumentOutOfRangeException(nameof(openDuration), "Must be positive.");
        if (halfOpenSuccessesToClose < 1)
            throw new ArgumentOutOfRangeException(nameof(halfOpenSuccessesToClose), "Must be at least 1.");

        _failureThreshold = failureThreshold;
        _openDuration = openDuration;
        _halfOpenSuccessesToClose = halfOpenSuccessesToClose;
        _timeProvider = timeProvider ?? TimeProvider.System;
    }

    /// <summary>
    /// Executes the supplied action through the circuit breaker.
    /// </summary>
    /// <typeparam name="T">The return type of the action.</typeparam>
    /// <param name="action">
    /// The async operation to protect. Receives a <see cref="CancellationToken"/> that
    /// combines the caller's token with any internal cancellation.
    /// </param>
    /// <param name="ct">Caller-supplied cancellation token.</param>
    /// <returns>The value returned by <paramref name="action"/>.</returns>
    /// <exception cref="CircuitOpenException">
    /// Thrown immediately when the circuit is <see cref="CircuitState.Open"/>.
    /// </exception>
    public async Task<T> ExecuteAsync<T>(Func<CancellationToken, Task<T>> action, CancellationToken ct = default)
    {
        // Check state and possibly transition Open→HalfOpen before the call.
        CircuitState stateBeforeCall = PrepareCall();

        if (stateBeforeCall == CircuitState.Open)
            throw new CircuitOpenException("Circuit is open; request fast-failed.");

        // Execute outside the lock to allow concurrency and avoid deadlocks.
        T result;
        try
        {
            result = await action(ct).ConfigureAwait(false);
        }
        catch (Exception)
        {
            RecordFailure();
            throw;
        }

        RecordSuccess();
        return result;
    }

    /// <summary>
    /// Reads the current state, transitions Open→HalfOpen if the cooldown has elapsed,
    /// and returns the effective state for this call.
    /// </summary>
    private CircuitState PrepareCall()
    {
        CircuitState? transition = null;
        CircuitState effective;

        lock (_lock)
        {
            if (_state == CircuitState.Open)
            {
                TimeSpan elapsed = _timeProvider.GetElapsedTime(_openedAtTimestamp);
                if (elapsed >= _openDuration)
                {
                    transition = CircuitState.HalfOpen;
                    TransitionTo(CircuitState.HalfOpen);
                    effective = CircuitState.HalfOpen;
                }
                else
                {
                    effective = CircuitState.Open;
                }
            }
            else
            {
                effective = _state;
            }
        }

        if (transition.HasValue)
            StateChanged?.Invoke(this, transition.Value);

        return effective;
    }

    /// <summary>Records a successful action outcome and performs any state transition.</summary>
    private void RecordSuccess()
    {
        CircuitState? transition = null;

        lock (_lock)
        {
            if (_state == CircuitState.Closed)
            {
                _consecutiveFailures = 0;
            }
            else if (_state == CircuitState.HalfOpen)
            {
                _halfOpenSuccesses++;
                if (_halfOpenSuccesses >= _halfOpenSuccessesToClose)
                {
                    transition = CircuitState.Closed;
                    TransitionTo(CircuitState.Closed);
                }
            }
            // If state changed under us (e.g. another thread reopened while we succeeded), ignore.
        }

        if (transition.HasValue)
            StateChanged?.Invoke(this, transition.Value);
    }

    /// <summary>Records a failed action outcome and performs any state transition.</summary>
    private void RecordFailure()
    {
        CircuitState? transition = null;

        lock (_lock)
        {
            if (_state == CircuitState.Closed)
            {
                _consecutiveFailures++;
                if (_consecutiveFailures >= _failureThreshold)
                {
                    transition = CircuitState.Open;
                    TransitionTo(CircuitState.Open);
                }
            }
            else if (_state == CircuitState.HalfOpen)
            {
                // Any failure in HalfOpen reopens the circuit.
                transition = CircuitState.Open;
                TransitionTo(CircuitState.Open);
            }
        }

        if (transition.HasValue)
            StateChanged?.Invoke(this, transition.Value);
    }

    /// <summary>
    /// Updates state fields to match <paramref name="newState"/>.
    /// Must be called while holding <c>_lock</c>.
    /// </summary>
    private void TransitionTo(CircuitState newState)
    {
        _state = newState;

        switch (newState)
        {
            case CircuitState.Open:
                _openedAtTimestamp = _timeProvider.GetTimestamp();
                _halfOpenSuccesses = 0;
                break;
            case CircuitState.HalfOpen:
                _consecutiveFailures = 0;
                _halfOpenSuccesses = 0;
                break;
            case CircuitState.Closed:
                _consecutiveFailures = 0;
                _halfOpenSuccesses = 0;
                break;
        }
    }
}
