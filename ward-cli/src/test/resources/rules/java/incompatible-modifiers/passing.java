@IncompatibleModifiers(Modifier.FINAL)
@interface Marked {
}

class Passing {
  @Marked
  int b;
  final int c = 1;
}
