class Registry {
  private static Registry instance = new Registry();

  private Registry() {
  }

  public Object clone() {
    return instance;
  }
}
