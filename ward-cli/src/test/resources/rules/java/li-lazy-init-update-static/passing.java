class Holder {
  private static Cache cache;

  static Cache get() {
    if (cache == null) {
      cache = new Cache();
    }
    return cache;
  }
}
