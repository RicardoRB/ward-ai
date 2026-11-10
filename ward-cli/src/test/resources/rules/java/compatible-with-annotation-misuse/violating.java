class T<K> {
  boolean ok(@CompatibleWith("K") Object o) { return true; }
  boolean bad(@CompatibleWith("Q") Object o) { return true; }
}
