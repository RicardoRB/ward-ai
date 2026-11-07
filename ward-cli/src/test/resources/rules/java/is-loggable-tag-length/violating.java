final class Logs {
  boolean on() {
    return android.util.Log.isLoggable("abcdefghijklmnopqrstuvwxyz", 3);
  }
}
