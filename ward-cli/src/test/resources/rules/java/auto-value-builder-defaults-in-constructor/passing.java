


@AutoValue.Builder
abstract class Quiet {
  Quiet() {}

  abstract Quiet setName(String name);
}

abstract class Other {
  Other() {
    init();
  }
}
