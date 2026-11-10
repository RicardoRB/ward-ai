class Registry {
  private static Registry instance = new Registry();

  public Registry() {
  }

  public static Registry getInstance() {
    return instance;
  }
}
