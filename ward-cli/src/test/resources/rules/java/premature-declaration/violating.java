class Violating {
  int run(String[] strings) {
    int length = 0;
    if (strings == null) {
      return 0;
    }
    for (String s : strings) {
      length += s.length();
    }
    return length;
  }
}
