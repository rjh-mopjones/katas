namespace Katas.Generics;

/// <summary>
/// A generic interface whose type parameter <typeparamref name="T"/> is <b>covariant</b> (<c>out</c>).
/// </summary>
public interface IProducer<out T>
{
    /// <summary>Produces a value of type <typeparamref name="T"/>.</summary>
    T Produce();
}

/// <summary>
/// A generic interface whose type parameter <typeparamref name="T"/> is <b>contravariant</b> (<c>in</c>).
/// </summary>
public interface IConsumer<in T>
{
    /// <summary>Consumes <paramref name="item"/>.</summary>
    void Consume(T item);
}

/// <summary>
/// A concrete <see cref="IProducer{T}"/> that always produces a constant <see cref="string"/>.
/// </summary>
public sealed class StringProducer : IProducer<string>
{
    /// <summary>Initialises the producer with a constant value to return.</summary>
    public StringProducer(string value) => throw new NotImplementedException();

    /// <inheritdoc/>
    public string Produce() => throw new NotImplementedException();
}

/// <summary>
/// A concrete <see cref="IConsumer{T}"/> that accepts any <see cref="object"/> by recording
/// its string representation.
/// </summary>
public sealed class ObjectConsumer : IConsumer<object>
{
    /// <summary>The string representations of all consumed objects, in order.</summary>
    public IReadOnlyList<string> Received => throw new NotImplementedException();

    /// <inheritdoc/>
    public void Consume(object item) => throw new NotImplementedException();
}
