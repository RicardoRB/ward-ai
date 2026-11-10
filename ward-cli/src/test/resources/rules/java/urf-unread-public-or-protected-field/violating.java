final class Check {
  private static final class Holder {
    public int count;
  }

  void test(Holder holder) {
    holder.count = 1;
  }
}
