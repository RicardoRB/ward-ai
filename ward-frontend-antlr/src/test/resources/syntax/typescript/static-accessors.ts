class Config {
  static get version(): string {
    return "1";
  }
  static set version(next: string) {
    Config.last = next;
  }
  static last = "";
}
