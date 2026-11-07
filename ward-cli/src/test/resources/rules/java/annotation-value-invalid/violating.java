class Contracts {
  private String name;

  @RequiresNonNull("missing")
  void run() {}

  @EnsuresNonNull({"name", "this.other"})
  void init() {
    name = "";
  }
}
