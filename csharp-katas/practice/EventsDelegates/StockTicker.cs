namespace Katas.EventsDelegates;

/// <summary>
/// Event arguments carrying the new price after a stock price change.
/// </summary>
public sealed class PriceChangedEventArgs : EventArgs
{
    /// <summary>The new stock price that triggered the event.</summary>
    public decimal Price { get; }

    /// <summary>Initialises the args with the new <paramref name="price"/>.</summary>
    public PriceChangedEventArgs(decimal price) => Price = price;
}

/// <summary>
/// Demonstrates the standard .NET event pattern: a mutable price that raises
/// <see cref="PriceChanged"/> whenever it changes.
/// </summary>
public class StockTicker
{
    /// <summary>Raised whenever <see cref="UpdatePrice"/> changes the current price.</summary>
    public event EventHandler<PriceChangedEventArgs>? PriceChanged;

    /// <summary>Current price of the tracked stock.</summary>
    public decimal CurrentPrice => throw new NotImplementedException();

    /// <summary>Updates the current price and raises <see cref="PriceChanged"/> if the value changed.</summary>
    public void UpdatePrice(decimal newPrice) => throw new NotImplementedException();

    /// <summary>Raises the <see cref="PriceChanged"/> event.</summary>
    protected virtual void OnPriceChanged(PriceChangedEventArgs args) => throw new NotImplementedException();
}
