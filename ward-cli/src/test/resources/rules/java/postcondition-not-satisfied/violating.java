class Contracts {
  private String name;
  private String size;

  @EnsuresNonNull("name")
  void init() {
    size = "";
  }

  @EnsuresNonNull({"name", "size"})
  void both() {
    this.name = "";
  }
}
