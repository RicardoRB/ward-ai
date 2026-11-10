final class Check {
  void test(Api api, boolean flag) {
    String value = null;
    if (flag) {
      value = "x";
    }
    api.take(value);
  }

  interface Api {
    void take(@javax.annotation.Nonnull String value);
  }
}
