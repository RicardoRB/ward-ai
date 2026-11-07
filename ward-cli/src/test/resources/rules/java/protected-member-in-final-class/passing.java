class Base {
  protected void run() {
    System.out.println("run");
  }
}

final class Leaf extends Base {
  @Override
  protected void run() {
    System.out.println("leaf");
  }
}
