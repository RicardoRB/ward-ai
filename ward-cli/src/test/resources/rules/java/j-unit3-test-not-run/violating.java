import junit.framework.TestCase;

class Violating extends TestCase {
  void testHidden() {
  }
  public void testWithArgument(int a) {
  }
  public int testReturns() {
    return 1;
  }
}
