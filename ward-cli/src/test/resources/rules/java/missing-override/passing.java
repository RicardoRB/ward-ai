interface Api {
  /** Runs it. */
  void run();
}
class Impl implements Runnable {
  /** Runs it. {@inheritDoc} */
  @Override public void run() {}
}
