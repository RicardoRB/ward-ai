import java.util.concurrent.locks.Lock;

class Sample {
  private final Lock lock;

  Sample(Lock lock) {
    this.lock = lock;
  }

  void release() {
    lock.unlock();
  }
}

