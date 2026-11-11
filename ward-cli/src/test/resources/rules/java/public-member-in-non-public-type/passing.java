public final class Shown {
  public void run() {
    System.out.println("run");
  }

  private static final class Hidden implements Runnable {
    @Override
    public void run() {}

    public static void main(String[] args) {}

    void helper() {}
  }

  public interface Port {
    class Impl implements Port {
      public void go() {}
    }

    void go();
  }
}
