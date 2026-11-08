final class Check {
  @FunctionalInterface
  interface Handler {
    void handle();
  }

  @FunctionalInterface
  interface Task extends Handler {
    void run();

    @Override
    default void handle() { run(); run(); }
  }
}
