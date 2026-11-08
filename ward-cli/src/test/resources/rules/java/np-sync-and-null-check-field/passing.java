class Sample {
  private Object lock = new Object();
  private Object other;

  void run() {
    synchronized (lock) {
      if (other == null) {
        return;
      }
    }
  }
}

