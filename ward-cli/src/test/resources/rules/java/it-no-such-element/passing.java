class Sample implements java.util.Iterator<String> {
  public boolean hasNext() {
    return false;
  }
  public String next() {
    throw new java.util.NoSuchElementException();
  }
}
