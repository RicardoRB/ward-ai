import java.util.Objects;

class Sample {
  String first;
  String second;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Sample)) {
      return false;
    }
    Sample that = (Sample) o;
    return Objects.equals(this.first, that.second);
  }
}
