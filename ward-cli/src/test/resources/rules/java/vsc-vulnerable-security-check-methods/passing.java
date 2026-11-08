class Check {
  final void locked() {
    System.getSecurityManager().checkRead("file");
  }

  private void hidden() {
    System.getSecurityManager().checkRead("file");
  }

  void plain() {
    System.out.println("file");
  }
}

final class Sealed {
  void test() {
    System.getSecurityManager().checkRead("file");
  }
}
