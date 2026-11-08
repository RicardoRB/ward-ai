class Clock implements Runnable {
  private long now;

  public void run() {
    now = System.nanoTime();
  }

  public long read() {
    return now;
  }
}
