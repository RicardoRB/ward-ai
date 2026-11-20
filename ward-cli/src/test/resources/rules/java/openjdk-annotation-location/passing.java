final class OpenjdkPassing {
  @Deprecated @Override void both() {}
  @Deprecated
  @Override
  void lines() {}
  @Deprecated void mixed() {}
  static void one(@Deprecated final String a) {}
  static void two(
      @Deprecated final String a,
      @Deprecated final String b
  ) {}
  static void formatted(
      @Deprecated @SuppressWarnings("x") final String a,
      final String b
  ) {}
}
