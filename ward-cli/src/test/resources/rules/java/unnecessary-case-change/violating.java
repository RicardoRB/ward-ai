class Roles {
  void check(String name) {
    if (name.toLowerCase().equals("admin")) {
      grant();
    }
  }

  void grant() {}
}
