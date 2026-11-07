final class UseStylePassing {
  @Deprecated(since = "1") void a() {}
  @SuppressWarnings("x") void b() {}
  @SuppressWarnings({"x", "y"}) void c() {}
}
