class Registry {
  private static Registry instance;

  public static synchronized Registry getInstance() {
    if (instance == null) {
      instance = new Registry();
    }
    return instance;
  }
}
