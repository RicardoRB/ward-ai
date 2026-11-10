import com.google.errorprone.annotations.Immutable;

@Immutable
class Violating {
  int count;
  final java.util.List<String> names = null;
  final int[] values = null;
  final int fine = 1;
}
