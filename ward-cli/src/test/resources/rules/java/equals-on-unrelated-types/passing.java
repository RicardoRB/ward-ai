class A {
  boolean f(String name, String other, Object any) {

    boolean same = name.equals(other);
    boolean unknown = name.equals(any);
    return same && unknown;
  }
}
