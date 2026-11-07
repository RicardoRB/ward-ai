class Waiters {
  void a(Object lock) {
    lock.notifyAll();
  }
}
