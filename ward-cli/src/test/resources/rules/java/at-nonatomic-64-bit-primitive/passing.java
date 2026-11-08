class Clock implements Runnable {
  private volatile long now;
  private int ticks;

  public void run() {
    now = System.nanoTime();
  }

  public long read() {
    return now;
  }
}
