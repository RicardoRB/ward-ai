public class Hooks {
  /** Runs the hook. */
  public void before() {
    run();
  }

  /** Extension point: called on start. */
  public void start() {
    run();
  }

  @Override
  public String toString() {
    return "";
  }

  private void run() {
  }
}
