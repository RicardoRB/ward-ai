using System.Collections.Generic;
namespace P;
public class A<U> {
  public void F(U x) {}
  public void G(List<U> x) {}
  protected void M() {}
  public int V { get; set; }
  public void Added() {}
  public virtual void AlsoAdded(int x = 3) {}
  internal void Inner() {}
}
public sealed class S {
}
