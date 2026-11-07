class Casts {
  String first(String maybe) {
    return castToNonNull(maybe);
  }

  String second() {
    return castToNonNull(lookup());
  }

  String lookup() {
    return "";
  }
}
