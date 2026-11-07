final class Check {
  int bucket(Object key, int size) {
    return Math.floorMod(key.hashCode(), size);
  }
}
