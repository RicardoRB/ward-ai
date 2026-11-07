final class Hashes {
  int slot(Object key) {
    return Math.abs(key.hashCode());
  }
}
