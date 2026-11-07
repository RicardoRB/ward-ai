final class T {
  void run(Service service) {
    // ok
    service.accept("x");
    service.other(null);
  }
}
