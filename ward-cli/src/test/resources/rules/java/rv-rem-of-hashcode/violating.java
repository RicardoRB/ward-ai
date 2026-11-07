final class Check {
  int bucket(Object key, int size) {
    return key.hashCode() % size;
  }
}
