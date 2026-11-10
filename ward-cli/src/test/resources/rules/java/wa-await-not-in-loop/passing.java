
class Sample {
  void run(java.util.concurrent.locks.Condition c) throws Exception {
    while (true) {
      c.await();
    }
  }
}
