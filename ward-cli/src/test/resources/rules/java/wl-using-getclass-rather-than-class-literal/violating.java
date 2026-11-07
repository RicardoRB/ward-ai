final class Sample {
  void run(Object lock, java.util.List<String> list, int n) throws Exception {
    synchronized (getClass()) {
      list.clear();
    }
  }
}
