final class Sample {
  void run(java.util.concurrent.locks.ReentrantLock lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
