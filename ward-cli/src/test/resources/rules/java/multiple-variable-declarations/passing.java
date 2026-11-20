class Passing {
  int a;
  int b;

  void method() {
    int c = 1;
    for (int i = 0; i < c; i++) {
    }
  }

  int size(Object shape) {
    final int size =
        switch (shape) {
          case String text -> text.length();
          case Point(int x, int y) -> x + y;
          default -> 0;
        };
    return size;
  }
}
