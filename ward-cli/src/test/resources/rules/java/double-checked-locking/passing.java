class Lazy {
  private Object value;
  Object get() {
    synchronized (this) {
      if (value == null) {
        value = new Object();
      }
    }
    return value;
  }
}
