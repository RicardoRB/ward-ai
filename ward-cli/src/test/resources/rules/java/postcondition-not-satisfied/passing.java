class Contracts {
  private String name;

  @EnsuresNonNull("name")
  void init() {
    this.name = "";
  }

  @EnsuresNonNull("name")
  abstract void declared();
}
