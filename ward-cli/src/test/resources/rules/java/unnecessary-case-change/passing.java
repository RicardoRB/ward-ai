class Roles {
  void check(String name) {
    if (name.equalsIgnoreCase("admin")) {
      grant();
    }
  }

  void grant() {}
}
