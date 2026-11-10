class Base {
  private String name;
  private String size;

  @RequiresNonNull("name")
  void run() {}
}

class Child extends Base {
  @RequiresNonNull({"name", "size"})
  void run() {}
}
