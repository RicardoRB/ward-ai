class Cache {
  private String value;
  private int hits;

  String get() {
    synchronized (this) {
      if (this.value == null) {
        this.value = "x";
      }
      return this.value;
    }
  }

  void touch() {
    synchronized (this) {
      hits++;
    }
    hits += 2;
  }

  void clear() {
    value = null;
    hits = 0;
  }

  @Override
  public String toString() {
    synchronized (this) {
      return value + hits;
    }
  }
}
