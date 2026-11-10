class Contracts {
  private String name;

  @RequiresNonNull("name")
  void run() {}

  @EnsuresNonNull({"name", "this.name", "name.value"})
  void init() {
    name = "";
  }
}

class Inherited extends Contracts {
  @RequiresNonNull("anything")
  void more() {}
}
