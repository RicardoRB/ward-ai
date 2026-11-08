class Account {
  private final String name;
  private final int limit;

  Account(String name, int limit) {
    this.name = name;
    this.limit = limit;
  }

  Account(String name) {
    this(name, 10);
  }

  Account(String name, long limit) {
    this(name.trim(), (int) limit);
  }

  Account() {
    this("none");
  }
}
