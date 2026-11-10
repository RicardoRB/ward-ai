class Lazy {
  private Object value;
  Object get() {
    if (value == null) {
      synchronized (this) {
        if (value == null) {
          value = new Object();
        }
      }
    }
    return value;
  }
}
