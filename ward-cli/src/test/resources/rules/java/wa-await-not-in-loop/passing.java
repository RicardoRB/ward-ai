final class Passing {
  void run(java.util.concurrent.locks.Condition notEmpty) throws Exception {
    while (notEmpty.hashCode() == 0) {
      notEmpty.await();
    }
  }
}
