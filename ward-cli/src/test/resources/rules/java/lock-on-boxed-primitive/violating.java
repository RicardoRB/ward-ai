final class Locks {
  void run(Integer lock) {
    synchronized (lock) {
      work();
    }
  }

  void work() {}
}
