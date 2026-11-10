class Sample {
  void run(java.util.concurrent.locks.Lock lock) {
    lock.lock();
    lock.hashCode();
    lock.unlock();
  }
}
