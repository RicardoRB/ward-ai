class Holder {
  private volatile Resource instance;

  Resource get() {
    if (instance == null) {
      synchronized (this) {
        if (instance == null) {
          instance = new Resource();
          instance.ready = true;
        }
      }
    }
    return instance;
  }
}

