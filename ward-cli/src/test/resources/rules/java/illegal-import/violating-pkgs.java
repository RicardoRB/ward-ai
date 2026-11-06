import java.util.List;
import sun.misc.Unsafe;

final class Illegal {
  List<String> names() {
    return List.of();
  }
}
