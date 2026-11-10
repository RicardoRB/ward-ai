class Semaphores {
  public synchronized void bad() throws InterruptedException {
    wait();
  }
}
