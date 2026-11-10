class Hits implements Runnable {
  private int hits;

  public void run() {
    hits++;
  }

  public int read() {
    return hits;
  }
}
