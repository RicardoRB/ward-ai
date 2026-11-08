class Check {
  void test() {
    System.getSecurityManager().checkRead("file");
  }
}
