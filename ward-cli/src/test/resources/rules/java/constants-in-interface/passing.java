interface Shape {
  double area();
}

final class Limits {
  static final int MAX = 10;
}

interface Counter {
  int START = 0;

  int next();
}
