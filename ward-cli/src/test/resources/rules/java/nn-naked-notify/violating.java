class Naked {
  void bad() {
    synchronized (this) {
      notify();
    }
  }
}
