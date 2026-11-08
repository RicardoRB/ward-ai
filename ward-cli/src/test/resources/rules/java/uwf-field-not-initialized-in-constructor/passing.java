final class Check {
  private String name;
  private String label;
  private String other;

  Check(String name) {
    this.name = name;
    other = name;
  }

  void init(String value) {
    name = value;
    label = value;
    other = value;
  }

  int length() {
    return name.length() + (label == null ? 0 : label.length());
  }

  int size() {
    return other.length();
  }
}
