final class Check {
  void test(Api api) {
    api.take(null);
  }

  interface Api {
    void take(@javax.annotation.Nonnull String value);
  }
}
