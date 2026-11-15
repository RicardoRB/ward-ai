final class Cursor {
  private final java.util.Iterator<String> items;

  public boolean hasNext() {
    return items.hasNext();
  }

  static final class Letters implements java.util.Iterator<String> {
    private final java.util.Iterator<String> source;
    private boolean more;

    @Override
    public boolean hasNext() {
      return more && source.next() != null;
    }

    @Override
    public String next() {
      return "a";
    }
  }
}
