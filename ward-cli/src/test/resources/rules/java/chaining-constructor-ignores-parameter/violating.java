class Account {
  private final String name;
  private final int limit;

  Account(String name, int limit) {
    this.name = name;
    this.limit = limit;
  }

  Account(String name) {
    this(null, 10);
  }
}
