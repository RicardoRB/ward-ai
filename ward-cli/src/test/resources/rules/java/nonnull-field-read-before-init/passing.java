import org.jspecify.annotations.NullMarked;

@NullMarked
class Reader {
  private String name;

  Reader(String name) {
    this.name = name;
    System.out.println(this.name);
  }

  Reader() {
    this.name = "";
    log(name);
  }

  void log(String value) {}
}
