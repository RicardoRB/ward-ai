namespace Shop;

/// <summary>Prices of the shop.</summary>
public static class Price
{
    /// <summary>Returns the total of <paramref name="quantity"/> items at <paramref name="unit"/> each.</summary>
    /// <param name="unit">The price of one item.</param>
    /// <param name="quantity">How many items; never negative.</param>
    /// <returns>The total price.</returns>
    public static decimal Total(decimal unit, int quantity)
    {
        if (quantity < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(quantity));
        }

        return unit * quantity;
    }
}
