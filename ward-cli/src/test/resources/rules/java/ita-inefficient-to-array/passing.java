class ToArrayOk {
  String[] good(java.util.List<String> xs) {
    return xs.toArray(new String[0]);
  }
}
