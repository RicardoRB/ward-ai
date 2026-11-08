final class T {
  Object run(Object executor) {
    return Futures.submitAsync(() -> null, executor);
  }
}
