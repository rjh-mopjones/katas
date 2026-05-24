namespace Katas.Iterators;

/// <summary>
/// A hand-rolled enumerable that counts from <see cref="From"/> down to 1.
/// </summary>
/// <remarks>
/// <para>
/// This class exists as a teaching exercise: it replicates exactly what the C# compiler
/// generates when you write a method with <c>yield return</c>.  Understanding the generated
/// state machine demystifies iterator blocks, disposal, and the two-interface contract.
/// </para>
///
/// <para><b>The iterator state machine pattern</b></para>
/// <para>
/// When you write:
/// <code>
/// IEnumerable&lt;int&gt; Countdown(int from) {
///     for (var i = from; i >= 1; i--) yield return i;
/// }
/// </code>
/// the C# compiler transforms the body into a nested private class (call it
/// <c>&lt;Countdown&gt;d__0</c>) that implements both <see cref="IEnumerable{T}"/>
/// and <see cref="IEnumerator{T}"/>.  The class carries:
/// <list type="bullet">
///   <item><description>A <c>state</c> field (−2 = not started / disposed, −1 = finished, 0 = before first MoveNext, n = resumption point n).</description></item>
///   <item><description>A <c>current</c> field that backs <see cref="IEnumerator{T}.Current"/>.</description></item>
///   <item><description>Copies of all local variables and parameters that span <c>yield return</c> points (here: <c>i</c>).</description></item>
/// </list>
/// <c>MoveNext</c> is a switch over the state field.  Each <c>yield return</c> site becomes a
/// numbered case: set <c>current</c>, advance the state, and return <c>true</c>.  When the
/// method body falls off the end the state is set to −1 and <c>false</c> is returned.
/// </para>
///
/// <para><b>Why implement both IEnumerable and IEnumerator on the same object?</b></para>
/// <para>
/// The compiler does this as a memory optimisation: the first call to
/// <see cref="GetEnumerator"/> returns <c>this</c> (if the object is in the initial state),
/// saving one heap allocation per <c>foreach</c>.  Subsequent calls to
/// <see cref="GetEnumerator"/> (e.g. a second nested <c>foreach</c>) must return a fresh
/// instance to avoid state corruption — which is exactly what <see cref="GetEnumerator"/>
/// does here.
/// </para>
/// </remarks>
public sealed class CountdownSequence : IEnumerable<int>, IEnumerator<int>
{
    // -------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------

    /// <summary>
    /// The inclusive upper bound supplied at construction time.
    /// </summary>
    public int From { get; }

    /// <summary>
    /// Iterator state value: -2 = not yet started (or already disposed),
    /// -1 = finished, ≥0 = running (value equals the next integer to emit).
    /// </summary>
    /// <remarks>
    /// The sentinel values −2 and −1 match the compiler-generated pattern so that
    /// disposing or exhausting the enumerator is idempotent and clearly detectable.
    /// </remarks>
    private int _state;

    private const int StateNotStarted = -2;
    private const int StateFinished = -1;

    /// <summary>
    /// The state to arm the machine with: the first value to emit, or <see cref="StateFinished"/>
    /// when <see cref="From"/> &lt; 1 so the sequence is correctly empty.
    /// </summary>
    private int ArmedState => From >= 1 ? From : StateFinished;

    // -------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------

    /// <summary>
    /// Initialises a new countdown sequence starting at <paramref name="from"/>.
    /// </summary>
    /// <param name="from">
    /// The first (largest) value in the sequence.  If <paramref name="from"/> is less
    /// than 1 the sequence will be empty (no elements are yielded).
    /// </param>
    public CountdownSequence(int from)
    {
        From = from;
        _state = StateNotStarted;
    }

    // -------------------------------------------------------------------
    // IEnumerable<int>
    // -------------------------------------------------------------------

    /// <inheritdoc/>
    /// <remarks>
    /// Returns <c>this</c> on the first call (optimisation: avoids an allocation when
    /// only one enumerator is ever created).  Subsequent calls return a fresh
    /// <see cref="CountdownSequence"/> so that nested or parallel enumeration is safe.
    /// Both paths return an enumerator whose state machine is already armed (ready for
    /// the first <see cref="MoveNext"/> call).
    /// </remarks>
    public IEnumerator<int> GetEnumerator()
    {
        // Return self if unused; otherwise a fresh instance (mirrors compiler output).
        if (_state == StateNotStarted)
        {
            _state = ArmedState; // arm the state machine
            return this;
        }
        // The fresh instance must also be armed so callers can call MoveNext immediately.
        var fresh = new CountdownSequence(From);
        fresh._state = ArmedState; // arm it directly
        return fresh;
    }

    System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() =>
        GetEnumerator();

    // -------------------------------------------------------------------
    // IEnumerator<int>
    // -------------------------------------------------------------------

    /// <inheritdoc/>
    /// <remarks>
    /// <c>Current</c> is undefined before the first <see cref="MoveNext"/> call and after
    /// <see cref="MoveNext"/> returns <c>false</c> — consistent with all BCL enumerators.
    /// </remarks>
    public int Current { get; private set; }

    object System.Collections.IEnumerator.Current => Current;

    /// <inheritdoc/>
    /// <remarks>
    /// Advances the enumerator to the next countdown value.
    /// The state field serves as both a "have we started?" flag and the current counter.
    /// </remarks>
    public bool MoveNext()
    {
        if (_state == StateFinished || _state == StateNotStarted)
            return false;

        // _state holds the value to emit next (counts down from From to 1).
        Current = _state;
        _state = _state > 1 ? _state - 1 : StateFinished;
        return true;
    }

    /// <inheritdoc/>
    /// <remarks>
    /// Resets the state machine to its initial position so the sequence can be
    /// re-enumerated.  The BCL's <c>yield</c>-generated iterators throw
    /// <see cref="NotSupportedException"/> here; we implement it properly to demonstrate
    /// what Reset truly means and to enable the test "two independent enumerations via Reset".
    /// </remarks>
    public void Reset() => _state = ArmedState;

    /// <inheritdoc/>
    /// <remarks>
    /// Marks the enumerator as finished so subsequent <see cref="MoveNext"/> calls
    /// return <c>false</c>.  There are no unmanaged resources, so no finaliser is needed.
    /// </remarks>
    public void Dispose() => _state = StateNotStarted;
}
