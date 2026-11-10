final class Sample {
  void run(Object lock) throws Exception {
    lock.notifyAll();
  }
}
