class Flag implements Runnable {
  private boolean done;

  public void run() {
    done = true;
  }

  public boolean isDone() {
    return done;
  }
}
