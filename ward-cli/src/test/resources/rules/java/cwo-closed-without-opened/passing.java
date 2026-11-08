import java.util.concurrent.locks.Lock;

class Sample {
  private final Lock lock;
  private int count;

  Sample(Lock lock) { this.lock = lock; }

  void work() {
    lock.lock();
    try {
      count++;
    } finally {
      lock.unlock();
    }
  }
}

