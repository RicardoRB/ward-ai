class ToArray {
  String[] bad(java.util.List<String> xs) {
    return xs.toArray(new String[xs.size()]);
  }
}
