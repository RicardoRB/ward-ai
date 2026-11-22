namespace Shop;

/// <summary>A class that no test reaches.</summary>
public static class Untested
{
    /// <summary>Returns a label for a number.</summary>
    /// <param name="value">The number.</param>
    /// <returns>The label.</returns>
    public static string Label(int value)
    {
        if (value < 0)
        {
            return "negative";
        }

        if (value == 0)
        {
            return "zero";
        }

        if (value < 10)
        {
            return "small";
        }

        return "large";
    }
}
