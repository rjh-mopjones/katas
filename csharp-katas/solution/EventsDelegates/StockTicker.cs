namespace Katas.EventsDelegates;

/// <summary>
/// Event arguments carrying the new price after a stock price change.
/// </summary>
/// <remarks>
/// <para>
/// Derives from <see cref="EventArgs"/> to follow the standard .NET event pattern.
/// The pattern requires:
/// <list type="number">
///   <item><description>An <c>EventArgs</c> subclass (this type).</description></item>
///   <item><description>An <c>event EventHandler&lt;TArgs&gt;</c> on the sender class.</description></item>
///   <item><description>A <c>protected virtual void On&lt;EventName&gt;</c> raising method so
///         subclasses can override the raise behaviour.</description></item>
/// </list>
/// Using <c>EventHandler&lt;T&gt;</c> (generic) rather than a custom delegate keeps the signature
/// uniform and compatible with the entire .NET event infrastructure (e.g. <c>Delegate.Combine</c>,
/// WPF binding, reactive extensions, etc.).
/// </para>
/// </remarks>
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
/// <remarks>
/// <para>
/// <b>Delegates vs events:</b>  An <c>event</c> keyword wraps a delegate with
/// <c>add</c>/<c>remove</c> accessors, preventing external code from invoking or replacing the
/// delegate.  Use <c>event</c> for public notification surfaces; use a bare delegate field when
/// you own the invocation entirely (e.g. inside a single class or in a strategy pattern).
/// </para>
/// <para>
/// <b>Virtual raising method:</b>  <c>OnPriceChanged</c> is <c>protected virtual</c> so a
/// subclass can intercept, suppress, or augment the event before it fires.  The null-conditional
/// operator (<c>?.Invoke</c>) safely handles the case where no subscribers are attached.
/// </para>
/// </remarks>
public class StockTicker
{
    private decimal _price;

    /// <summary>
    /// Raised whenever <see cref="UpdatePrice"/> changes the current price.
    /// </summary>
    public event EventHandler<PriceChangedEventArgs>? PriceChanged;

    /// <summary>Current price of the tracked stock.</summary>
    public decimal CurrentPrice => _price;

    /// <summary>
    /// Updates the current price and raises <see cref="PriceChanged"/> if the value changed.
    /// </summary>
    /// <param name="newPrice">The new price to apply.</param>
    public void UpdatePrice(decimal newPrice)
    {
        if (newPrice == _price) return;
        _price = newPrice;
        OnPriceChanged(new PriceChangedEventArgs(newPrice));
    }

    /// <summary>
    /// Raises the <see cref="PriceChanged"/> event.
    /// </summary>
    /// <param name="args">Event data including the new price.</param>
    /// <remarks>
    /// Subclasses can override this method to intercept the event before it reaches subscribers,
    /// or to suppress the event under certain conditions.  Always call <c>base.OnPriceChanged</c>
    /// unless you intentionally want to suppress the notification.
    /// </remarks>
    protected virtual void OnPriceChanged(PriceChangedEventArgs args)
    {
        // Thread-safe: copy the handler reference to a local before the null check.
        // Without this, a concurrent Unsubscribe could set the field to null between the check
        // and the invocation — the local copy retains a non-null reference.
        EventHandler<PriceChangedEventArgs>? handler = PriceChanged;
        handler?.Invoke(this, args);
    }
}
