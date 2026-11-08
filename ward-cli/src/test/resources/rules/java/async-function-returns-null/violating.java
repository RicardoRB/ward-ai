final class T {
  Object run(Object future, Object executor) {
    return Futures.transformAsync(future, input -> null, executor);
  }
}
