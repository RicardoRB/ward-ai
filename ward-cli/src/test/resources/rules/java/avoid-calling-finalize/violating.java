class Cleanup {
  void run(Object o) throws Throwable {
    o.finalize();
  }
}
