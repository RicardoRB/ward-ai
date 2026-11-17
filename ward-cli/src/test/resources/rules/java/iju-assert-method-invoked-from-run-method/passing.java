final class Worker implements Runnable {
  public void run() {
    System.out.println("done");
  }
}

final class Holder {
  void test() {
    final Runnable task =
        new Runnable() {
          public void run() {
            System.out.println("done");
          }
        };
    assertEquals(1, 1);
  }
}
