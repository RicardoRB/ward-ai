class Registry {
  private static Registry instance = new Registry();

  private Registry() {
  }

  public static Registry getInstance() {
    return instance;
  }
}
