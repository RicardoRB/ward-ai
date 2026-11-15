class Holder {
  String name = "a";

  void check() {
    String local = null;
  }
}

final class Store {
  private final String failure;
  private final String loaded;

  Store(String loaded) {
    this.loaded = loaded;
    this.failure = null;
  }

  Store() {
    loaded = "x";
    failure = null;
  }
}
