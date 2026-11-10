class Flag implements Runnable {
  private volatile boolean done;

  public void run() {
    done = true;
  }

  public boolean isDone() {
    return done;
  }
}

class Plain {
  private boolean done;

  void stop() {
    done = true;
  }

  boolean isDone() {
    return done;
  }
}
