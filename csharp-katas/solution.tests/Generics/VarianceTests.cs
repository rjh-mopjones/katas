namespace Katas.Tests.Generics;

using Katas.Generics;

public sealed class VarianceTests
{
    // -------------------------------------------------------------------------
    // Covariance: IProducer<string> assignable to IProducer<object>
    // -------------------------------------------------------------------------

    [Fact]
    public void Covariance_Should_AllowAssigning_StringProducerToObjectProducer()
    {
        // This assignment must compile: IProducer<string> → IProducer<object>
        // because IProducer<out T> is covariant.
        IProducer<string> stringProducer = new StringProducer("hello");
        IProducer<object> objectProducer = stringProducer;   // covariant widening

        object produced = objectProducer.Produce();
        Assert.Equal("hello", produced);
    }

    [Fact]
    public void Covariance_Should_ReturnCorrectValue_ThroughBaseInterfaceReference()
    {
        IProducer<object> producer = new StringProducer("world");
        Assert.IsType<string>(producer.Produce());
        Assert.Equal("world", producer.Produce());
    }

    // -------------------------------------------------------------------------
    // Contravariance: IConsumer<object> assignable to IConsumer<string>
    // -------------------------------------------------------------------------

    [Fact]
    public void Contravariance_Should_AllowAssigning_ObjectConsumerToStringConsumer()
    {
        // This assignment must compile: IConsumer<object> → IConsumer<string>
        // because IConsumer<in T> is contravariant.
        IConsumer<object> objectConsumer = new ObjectConsumer();
        IConsumer<string> stringConsumer = objectConsumer;   // contravariant widening

        stringConsumer.Consume("test");
        Assert.Equal("test", ((ObjectConsumer)objectConsumer).Received[0]);
    }

    [Fact]
    public void Contravariance_Should_RecordAllConsumedItems_ThroughDerivedInterfaceReference()
    {
        ObjectConsumer underlying = new();
        IConsumer<string> stringConsumer = underlying;   // contravariant

        stringConsumer.Consume("alpha");
        stringConsumer.Consume("beta");

        Assert.Equal(new[] { "alpha", "beta" }, underlying.Received);
    }

    // -------------------------------------------------------------------------
    // Concrete behaviour
    // -------------------------------------------------------------------------

    [Fact]
    public void StringProducer_Should_AlwaysReturnConfiguredString()
    {
        IProducer<string> p = new StringProducer("kata");
        Assert.Equal("kata", p.Produce());
        Assert.Equal("kata", p.Produce()); // idempotent
    }

    [Fact]
    public void ObjectConsumer_Should_RecordNullAsNullString()
    {
        ObjectConsumer c = new();
        c.Consume(null!);
        Assert.Equal("(null)", c.Received[0]);
    }
}
