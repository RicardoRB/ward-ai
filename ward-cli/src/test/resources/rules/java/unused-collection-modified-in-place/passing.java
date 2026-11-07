final class T {
  int run() {
    List<String> names = new ArrayList<>();
    names.add("a");
    return names.size();
  }

  List<String> other() {
    List<String> names = new ArrayList<>();
    names.add("a");
    return names;
  }
}
