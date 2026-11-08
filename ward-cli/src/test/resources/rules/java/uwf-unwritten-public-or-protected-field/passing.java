final class Check {
  private static final class Holder {
    public int count;
    protected int seen = 2;
    public int step;
  }

  int test(Holder holder) {
    holder.step++;
    holder.count = 3;
    return holder.count + holder.seen + holder.step;
  }

  public static final class Open {
    public int free;
  }
}
