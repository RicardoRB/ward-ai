import net.jcip.annotations.Immutable;

@Immutable
final class Point {
  private final int x = 1;
  static int z;
}

class Plain {
  int a;
}
