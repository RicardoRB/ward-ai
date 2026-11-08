final class Check {
  private static final class Holder {
    public int count;
    protected int seen = 2;
    private int hidden;
  }

  int test(Holder holder) {
    holder.count = 1;
    return holder.count + holder.seen;
  }

  public static final class Open {
    public int free;
  }
}
