final class T {
  Object run(Object executor) {
    return Futures.submitAsync(() -> Futures.immediateFuture(null), executor);
  }
}
