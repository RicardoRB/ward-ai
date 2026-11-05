package dataflow;

class Catches {
  void swallow() {
    try {
      System.out.println("x");
    } catch (Exception e) {
      System.out.println("y");
    }
  }
}
