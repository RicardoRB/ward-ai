import com.google.errorprone.annotations.Immutable;

@Immutable
class Passing {
  final int count = 1;
  final String name = "a";
  static int shared;
}
class Plain {
  int count;
}
