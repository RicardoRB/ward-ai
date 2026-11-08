class NakedOk {
  boolean ready;

  void good() {
    synchronized (this) {
      ready = true;
      notify();
    }
  }
}
