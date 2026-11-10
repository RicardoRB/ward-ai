final class Sample {
  void run(Object lock) {
    try {
      lock.notify();
    } catch (IllegalMonitorStateException e) {
      throw e;
    }
  }
}
