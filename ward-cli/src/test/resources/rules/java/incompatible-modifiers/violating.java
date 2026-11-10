@IncompatibleModifiers(Modifier.FINAL)
@interface Marked {
}

class Violating {
  @Marked
  final int a = 1;
  @Marked
  int b;
}
