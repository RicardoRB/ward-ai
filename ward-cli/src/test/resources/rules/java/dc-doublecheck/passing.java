final class Sample {
  void run(Object lock, java.util.List<String> list, int n) throws Exception {
    synchronized (list) {
      if (lock == null) {
        lock = new Object();
      }
    }
  }
}
