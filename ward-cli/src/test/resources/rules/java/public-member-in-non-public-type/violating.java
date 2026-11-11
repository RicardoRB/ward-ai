final class Hidden {
  public int size;

  public void run() {
    System.out.println("run");
  }

  public static final class Nested {
    public void nested() {}
  }

  Hidden() {}

  public Hidden(int size) {
    this.size = size;
  }
}

record Pair(int left, int right) {
  public Pair {
    left = Math.abs(left);
  }
}
