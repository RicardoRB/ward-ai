import org.jspecify.annotations.NullMarked;

@NullMarked
class Reader {
  private String name;
  private String size;

  Reader(String other) {
    System.out.println(name);
    this.name = other;
    this.size = other;
    String copy = this.size;
  }
}
