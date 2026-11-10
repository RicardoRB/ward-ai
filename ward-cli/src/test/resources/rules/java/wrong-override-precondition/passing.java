class Base {
  private String name;
  private String size;

  @RequiresNonNull({"name", "size"})
  void run() {}
}

class Child extends Base {
  @RequiresNonNull("name")
  void run() {}
}
