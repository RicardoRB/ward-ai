class Registry {
  private static Registry instance;

  static Registry get() {
    if (instance == null) {
      instance = new Registry();
    }
    return instance;
  }
}
