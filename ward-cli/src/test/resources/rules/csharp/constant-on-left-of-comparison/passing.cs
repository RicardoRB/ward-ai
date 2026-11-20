public class Names
{
    public bool Check(int value, string name, System.Text.StringBuilder text)
    {
        text.Append("\">");
        return value > 5 && name == "a";
    }
}
