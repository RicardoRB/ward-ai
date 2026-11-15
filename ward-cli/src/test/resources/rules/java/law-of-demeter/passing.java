package sample;

class Sample {
  void run(Other other) {
    use(other.get());
    this.get().use();
  }
  void more(Other other, java.util.List<String> names, Point point) {
    other.getName().trim();
    other.with().use();
    names.stream().count();
    point.scale().use();
    other.find().isPresent();
  }
}
