class Holder {
  private volatile Resource instance;

  Resource get() {
    if (instance == null) {
      synchronized (this) {
        if (instance == null) {
          Resource local = new Resource();
          local.ready = true;
          instance = local;
        }
      }
    }
    return instance;
  }
}

