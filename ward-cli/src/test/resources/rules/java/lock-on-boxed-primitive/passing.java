
final class Locks {
  void run(Object lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
