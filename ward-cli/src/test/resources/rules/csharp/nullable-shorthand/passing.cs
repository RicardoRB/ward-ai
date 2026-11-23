#define ENABLED
public class Names
{
    public int? Count;
    public System.Type Open = typeof(System.Nullable<>);
}
public class Conditional
{
#if NET20
    // List<Nullable<T>> in an inactive #if branch
    public Nullable<int> InIf;
#elif NET35
    public Nullable<int> InElif;
#else
    public int? Active;
#endif
#if ENABLED
    public int? Enabled;
#else
    public Nullable<int> InElse;
#endif
}
