namespace Katas.Generics;

/// <summary>
/// A generic interface whose type parameter <typeparamref name="T"/> is <b>covariant</b> (<c>out</c>).
/// </summary>
/// <typeparam name="T">
/// Produced (output-only) type.  Covariance (<c>out</c>) means an <c>IProducer&lt;Derived&gt;</c>
/// can be assigned to an <c>IProducer&lt;Base&gt;</c> variable.
/// </typeparam>
/// <remarks>
/// <para>
/// <b>Covariance rule:</b> The compiler allows <c>out T</c> only when <typeparamref name="T"/>
/// appears exclusively in output (return) positions.  If it appeared as a parameter type the
/// assignment <c>IProducer&lt;string&gt; s = ...; IProducer&lt;object&gt; o = s;</c> would be
/// unsafe because a caller could attempt to pass a non-string object through the parameter.
/// </para>
/// <para>
/// <b>Real-world examples:</b> <c>IEnumerable&lt;out T&gt;</c>, <c>IReadOnlyList&lt;out T&gt;</c>,
/// <c>Task&lt;out TResult&gt;</c>, <c>Func&lt;out TResult&gt;</c>.
/// </para>
/// </remarks>
public interface IProducer<out T>
{
    /// <summary>Produces a value of type <typeparamref name="T"/>.</summary>
    T Produce();
}

/// <summary>
/// A generic interface whose type parameter <typeparamref name="T"/> is <b>contravariant</b> (<c>in</c>).
/// </summary>
/// <typeparam name="T">
/// Consumed (input-only) type.  Contravariance (<c>in</c>) means an <c>IConsumer&lt;Base&gt;</c>
/// can be assigned to an <c>IConsumer&lt;Derived&gt;</c> variable — the direction is reversed
/// relative to covariance.
/// </typeparam>
/// <remarks>
/// <para>
/// <b>Contravariance rule:</b> The compiler allows <c>in T</c> only when <typeparamref name="T"/>
/// appears exclusively in input (parameter) positions.  A consumer of <c>object</c> can safely
/// handle any <c>string</c> because every <c>string</c> is an <c>object</c>.
/// </para>
/// <para>
/// <b>Real-world examples:</b> <c>Action&lt;in T&gt;</c>, <c>IComparer&lt;in T&gt;</c>,
/// <c>IEqualityComparer&lt;in T&gt;</c>.
/// </para>
/// <para>
/// <b>Mnemonic:</b> Producers are covariant (you get back what was promised — a more specific
/// type is still acceptable).  Consumers are contravariant (you accept a more general type —
/// you can always handle something more specific than you declared).
/// PECS: Producer Extends, Consumer Super (Java equivalent).
/// </para>
/// </remarks>
public interface IConsumer<in T>
{
    /// <summary>Consumes <paramref name="item"/>.</summary>
    void Consume(T item);
}

/// <summary>
/// A concrete <see cref="IProducer{T}"/> that always produces a constant <see cref="string"/>.
/// </summary>
/// <remarks>
/// Because <see cref="IProducer{T}"/> is covariant in <typeparamref name="T"/>, a
/// <c>StringProducer</c> can be assigned to <c>IProducer&lt;object&gt;</c> without a cast.
/// </remarks>
public sealed class StringProducer : IProducer<string>
{
    private readonly string _value;

    /// <summary>Initialises the producer with a constant value to return.</summary>
    public StringProducer(string value) => _value = value;

    /// <inheritdoc/>
    public string Produce() => _value;
}

/// <summary>
/// A concrete <see cref="IConsumer{T}"/> that accepts any <see cref="object"/> by recording
/// its string representation.
/// </summary>
/// <remarks>
/// Because <see cref="IConsumer{T}"/> is contravariant in <typeparamref name="T"/>, an
/// <c>ObjectConsumer</c> can be assigned to <c>IConsumer&lt;string&gt;</c>: a consumer that
/// can handle any object can certainly handle the narrower contract of handling only strings.
/// </remarks>
public sealed class ObjectConsumer : IConsumer<object>
{
    private readonly List<string> _received = new();

    /// <summary>The string representations of all consumed objects, in order.</summary>
    public IReadOnlyList<string> Received => _received;

    /// <inheritdoc/>
    public void Consume(object item) => _received.Add(item?.ToString() ?? "(null)");
}
