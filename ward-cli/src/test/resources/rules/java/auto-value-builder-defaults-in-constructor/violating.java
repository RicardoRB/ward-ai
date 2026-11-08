@AutoValue.Builder
abstract class Builder {
  Builder() {
    setName("x");
  }

  abstract Builder setName(String name);
}
