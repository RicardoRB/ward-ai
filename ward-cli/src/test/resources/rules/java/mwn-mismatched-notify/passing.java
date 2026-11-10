final class Sample {
  void run(Object lock) throws Exception {
    synchronized (lock) {
      lock.notifyAll();
    }
  }
}
