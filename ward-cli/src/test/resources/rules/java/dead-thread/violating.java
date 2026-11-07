final class Threads {
  void run(Runnable task) {
    new Thread(task);
  }
}
