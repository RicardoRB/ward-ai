class Sample {
  private Object lock = new Object();

  void run() {
    synchronized (lock) {
      if (lock == null) {
        return;
      }
    }
  }
}

