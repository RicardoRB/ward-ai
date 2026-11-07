class Singleton {
  private static final Singleton INSTANCE = new Singleton();
  static Singleton get() {
    if (INSTANCE == null) {
      return null;
    }
    return INSTANCE;
  }
}
