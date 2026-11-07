final class Check {
  void run(Runnable task) {
    try {
      task.run();
    } catch (IllegalStateException e) {
      return;
    }
  }
}
