class Entries implements java.util.Iterator<java.util.Map.Entry<String, String>> {
  private java.util.Map.Entry<String, String> current;

  public boolean hasNext() {
    return false;
  }

  public java.util.Map.Entry<String, String> next() {
    return new java.util.AbstractMap.SimpleEntry<>("a", "b");
  }
}

