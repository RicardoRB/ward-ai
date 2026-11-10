final class Sample {
  void run(Object lock) {
    try {
      lock.notify();
    } catch (RuntimeException e) {
      throw e;
    }
  }
}
