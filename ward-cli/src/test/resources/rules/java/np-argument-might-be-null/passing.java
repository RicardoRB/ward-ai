// passes
final class Check {
  int test(@CheckForNull String name, @Nullable String other) {
    if (name == null) {
      return other.length();
    }
    return name.length();
  }
}
