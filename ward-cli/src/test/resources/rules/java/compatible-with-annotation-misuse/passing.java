


class T<K> {
  boolean ok(@CompatibleWith("K") Object o) { return true; }
  <Q> boolean fine(@CompatibleWith("Q") Object o) { return true; }
  boolean plain(Object o) { return true; }
}
