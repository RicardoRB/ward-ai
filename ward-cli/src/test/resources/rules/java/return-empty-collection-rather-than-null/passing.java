import java.util.Collections;
import java.util.List;

class Sample {
  List<String> names(boolean b) {
    return Collections.emptyList();
  }

  String text() { return null; }

  Object later() {
    return (java.util.function.Supplier<List<String>>) () -> null;
  }
}
