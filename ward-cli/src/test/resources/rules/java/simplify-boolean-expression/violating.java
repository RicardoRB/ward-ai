class Flags {
  boolean a(boolean x) {
    return x == true;
  }

  boolean b(boolean x) {
    return x && true;
  }

  boolean c() {
    return !false;
  }

  boolean d(boolean x) {
    return x ? true : false;
  }
}
