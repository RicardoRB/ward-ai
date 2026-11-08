final class Violating {
  void run(java.util.concurrent.locks.Condition notEmpty) throws Exception {
    notEmpty.await();
  }
}
