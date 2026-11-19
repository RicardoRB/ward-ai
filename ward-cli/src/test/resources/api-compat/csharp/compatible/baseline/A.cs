using System.Collections.Generic;
namespace P;
public class A<T> {
  public void F(T x) {}
  public void G(List<T> x) {}
  protected void M() {}
  public int V { get; set; }
}
public sealed class S {
  protected void Hidden() {}
}
