using Xunit;

namespace Shop.Tests;

public class FailingTests
{
    [Fact]
    public void TotalIsWrong()
    {
        Assert.Equal(7m, Price.Total(2m, 3));
    }
}
