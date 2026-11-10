
final class Sample {
  void run(Object lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
