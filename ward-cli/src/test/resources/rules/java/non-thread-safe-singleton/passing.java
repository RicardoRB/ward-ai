class Singleton {
  private static final Singleton INSTANCE = new Singleton();
  static Singleton get() {
    if (INSTANCE == null) {
      return null;
    }
    return INSTANCE;
  }
}

class Defaulted {
  private final Object context;

  Defaulted(Object context) {
    if (context == null) {
      context = new Object();
    }
    this.context = context;
  }
}
