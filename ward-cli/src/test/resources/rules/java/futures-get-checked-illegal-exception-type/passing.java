
final class Waits {
  Object run(Object future) {
    return com.google.common.util.concurrent.Futures.getChecked(future, java.io.IOException.class);
  }
}
