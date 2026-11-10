// passes
final class Check {
  void test(Api api) {
    api.take("x");
    api.put(null);
  }

  interface Api {
    void take(@javax.annotation.Nonnull String value);

    void put(String value);
  }
}
