class Singleton {
  private static Singleton instance;
  static Singleton get() {
    if (instance == null) {
      instance = new Singleton();
    }
    return instance;
  }
}
