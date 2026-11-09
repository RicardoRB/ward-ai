class Config {
  static get version() {
    return "1";
  }
  static set version(next) {
    Config.last = next;
  }
  static last = "";
}
