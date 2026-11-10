class Passing {
  void run(boolean a) {
    if (a) {
      go();
    }
    try {
      go();
    } finally {
      go();
    }
    synchronized (this) {
      go();
    }
  }
  void go() {
  }
}
