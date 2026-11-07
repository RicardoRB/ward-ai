class SemaphoresOk {
  private final Object lock = new Object();

  public void good() throws InterruptedException {
    synchronized (lock) {
      lock.wait();
    }
  }
}
