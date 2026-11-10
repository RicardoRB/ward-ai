final class Hashes {
  int slot(Object key) {
    return key.hashCode() & 0x7fffffff;
  }
}
