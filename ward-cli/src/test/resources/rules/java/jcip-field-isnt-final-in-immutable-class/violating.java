import net.jcip.annotations.Immutable;

@Immutable
class Point {
  int x;
  private final int y = 1;
  static int z;
}
