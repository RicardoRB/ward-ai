final class Check {
  private static final class Holder {
    public int count;
  }

  int test(Holder holder) {
    return holder.count;
  }
}
