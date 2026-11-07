final class Waits {
  Object run(Object future) {
    return com.google.common.util.concurrent.Futures.getChecked(future, RuntimeException.class);
  }
}
