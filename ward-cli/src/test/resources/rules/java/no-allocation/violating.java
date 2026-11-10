final class T {
  @NoAllocation
  int run() {
    return new Object().hashCode();
  }
}
