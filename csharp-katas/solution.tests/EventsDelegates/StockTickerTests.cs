namespace Katas.Tests.EventsDelegates;

using Katas.EventsDelegates;

public sealed class StockTickerTests
{
    [Fact]
    public void UpdatePrice_Should_RaisePriceChanged_WithCorrectArgs()
    {
        StockTicker ticker = new();
        PriceChangedEventArgs? captured = null;
        ticker.PriceChanged += (_, args) => captured = args;

        ticker.UpdatePrice(99.50m);

        Assert.NotNull(captured);
        Assert.Equal(99.50m, captured.Price);
    }

    [Fact]
    public void UpdatePrice_Should_NotRaisePriceChanged_WhenPriceUnchanged()
    {
        StockTicker ticker = new();
        ticker.UpdatePrice(10m);

        int eventCount = 0;
        ticker.PriceChanged += (_, _) => eventCount++;

        ticker.UpdatePrice(10m); // same price — should not fire

        Assert.Equal(0, eventCount);
    }

    [Fact]
    public void UpdatePrice_Should_RaiseMultipleEvents_ForMultiplePriceChanges()
    {
        StockTicker ticker = new();
        var prices = new List<decimal>();
        ticker.PriceChanged += (_, args) => prices.Add(args.Price);

        ticker.UpdatePrice(1m);
        ticker.UpdatePrice(2m);
        ticker.UpdatePrice(3m);

        Assert.Equal(new[] { 1m, 2m, 3m }, prices);
    }

    [Fact]
    public void UpdatePrice_Should_ProvideCorrectSender_InEventArgs()
    {
        StockTicker ticker = new();
        object? sender = null;
        ticker.PriceChanged += (s, _) => sender = s;

        ticker.UpdatePrice(5m);

        Assert.Same(ticker, sender);
    }

    [Fact]
    public void PriceChanged_Should_DeliverToMultipleSubscribers()
    {
        StockTicker ticker = new();
        int calls = 0;
        ticker.PriceChanged += (_, _) => calls++;
        ticker.PriceChanged += (_, _) => calls++;

        ticker.UpdatePrice(7m);

        Assert.Equal(2, calls);
    }

    [Fact]
    public void CurrentPrice_Should_ReflectMostRecentPrice()
    {
        StockTicker ticker = new();
        ticker.UpdatePrice(15m);
        ticker.UpdatePrice(20m);

        Assert.Equal(20m, ticker.CurrentPrice);
    }
}
