class Registry {
  private static Registry plain;
  private static volatile Registry safe;
  private static int number;
  private static Registry viaSync;

  static synchronized Registry a() {
    if (plain == null) {
      plain = new Registry();
    }
    return plain;
  }

  static Registry b() {
    if (safe == null) {
      safe = new Registry();
    }
    return safe;
  }

  static Registry c() {
    if (viaSync == null) {
      synchronized (Registry.class) {
        viaSync = new Registry();
      }
    }
    return viaSync;
  }

  static Registry d() {
    synchronized (Registry.class) {
      if (plain == null) {
        plain = new Registry();
      }
    }
    return plain;
  }

  static int e() {
    if (number == 0) {
      number = 1;
    }
    return number;
  }
}
