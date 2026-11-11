class Flags {
  boolean a(boolean x, boolean y) {
    return x == y;
  }

  boolean b(boolean x, boolean y) {
    return x && !y;
  }

  boolean c(boolean x) {
    return !x;
  }

  boolean d(boolean x) {
    return x ? Boolean.TRUE : Boolean.FALSE;
  }
}
