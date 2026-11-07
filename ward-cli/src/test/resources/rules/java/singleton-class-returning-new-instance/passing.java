class Config {
  private static Config instance;

  Config getInstance() {
    if (instance == null) {
      instance = new Config();
    }
    return instance;
  }
}
