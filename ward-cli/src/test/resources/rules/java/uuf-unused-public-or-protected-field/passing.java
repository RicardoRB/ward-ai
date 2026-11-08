final class Check {
  private static final class Holder {
    public int count;
    protected int seen = 2;
  }

  int test(Holder holder) {
    return holder.count + holder.seen;
  }

  public static final class Open {
    public int free;
  }
}
