class Q
{
    void M(int[] numbers)
    {
        var evens = from n in numbers
        where n % 2 == 0
        select n;
    }
}
