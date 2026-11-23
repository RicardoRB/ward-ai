#define ENABLED
public class Names
{
    public Nullable<int> Count;
}
public class Conditional
{
#if NET20
    public Nullable<int> InIf;
#elif ENABLED
    public Nullable<int> InElif;
#endif
}
