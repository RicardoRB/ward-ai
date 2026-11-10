final class T {
  @NoAllocation
  int run() {
    return 1;
  }

  int other() {
    return new Object().hashCode();
  }
}
