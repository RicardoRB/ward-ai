final class Violating {
  void run(int a) {
    try {
      a++;
    } catch (Exception e) {
      a--;
    }
  }
}
