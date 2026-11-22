using Xunit;

namespace Shop.Tests;

public class PriceTests
{
    [Fact]
    public void TotalMultipliesUnitByQuantity()
    {
        Assert.Equal(6m, Price.Total(2m, 3));
    }

    [Fact]
    public void TotalOfNoItemsIsZero()
    {
        Assert.Equal(0m, Price.Total(2m, 0));
    }

    [Fact]
    public void TotalRejectsANegativeQuantity()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Price.Total(2m, -1));
    }
}
