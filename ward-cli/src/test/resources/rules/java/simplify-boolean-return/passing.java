final class Flags {
  boolean ready(final int count) {
    if (count > 0) {
      return count > 1;
    }
    return false;
  }
}
