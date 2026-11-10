interface Violating {
  default void first() {}

  static void second() {}

  void third();

  int LIMIT = 1;
}
