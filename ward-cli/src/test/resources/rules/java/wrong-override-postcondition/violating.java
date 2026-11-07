class Base {
  private String name;

  @EnsuresNonNull("name")
  void init() {
    name = "";
  }
}

class Child extends Base {
  void init() {}
}
